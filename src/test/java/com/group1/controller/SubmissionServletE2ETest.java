package com.group1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SubmissionServletE2ETest {
    private static final String BOUNDARY = "----AitaBoundary7MA4YWxkTrZu0gW";

    private Server server;
    private FakeSubmissionDatabase database;
    private String baseUrl;

    @Before
    public void startServletContainer() throws Exception {
        database = new FakeSubmissionDatabase();
        server = new Server(0);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");

        ServletHolder submission = new ServletHolder(new TestSubmissionController(database));
        context.addServlet(submission, "/student/submissions");
        submission.getRegistration().setMultipartConfig(new MultipartConfigElement(
                System.getProperty("java.io.tmpdir"),
                11L * 1024 * 1024,
                11L * 1024 * 1024,
                1024 * 1024));
        context.addServlet(new ServletHolder(new TestLoginServlet()), "/test-login");

        server.setHandler(context);
        server.start();
        int port = ((ServerConnector) server.getConnectors()[0]).getLocalPort();
        baseUrl = "http://127.0.0.1:" + port;
    }

    @After
    public void stopServletContainer() throws Exception {
        if (server != null) {
            server.stop();
            server.join();
        }
    }

    @Test
    public void submissionServletHashesAndRejectsDuplicateZipUploads() throws Exception {
        byte[] zip = createZip();
        String cookie = loginAndGetSessionCookie();

        HttpResult unauthorized = upload(zip, null);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, unauthorized.status);

        HttpResult accepted = upload(zip, cookie);
        assertEquals(HttpServletResponse.SC_CREATED, accepted.status);
        String expectedHash = hex(MessageDigest.getInstance("SHA-256").digest(zip));
        assertTrue(accepted.body.contains("\"sha256\":\"" + expectedHash + "\""));
        assertTrue(database.containsHash(expectedHash));

        HttpResult duplicate = upload(zip, cookie);
        assertEquals(HttpServletResponse.SC_CONFLICT, duplicate.status);
    }

    private String loginAndGetSessionCookie() throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/test-login").openConnection();
        try {
            assertEquals(HttpServletResponse.SC_NO_CONTENT, connection.getResponseCode());
            String setCookie = connection.getHeaderField("Set-Cookie");
            assertTrue("The login endpoint must establish an HTTP session.", setCookie != null);
            return setCookie.substring(0, setCookie.indexOf(';'));
        } finally {
            connection.disconnect();
        }
    }

    private HttpResult upload(byte[] zip, String cookie) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write(body, "--" + BOUNDARY + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"submissionFile\"; filename=\"project.zip\"\r\n");
        write(body, "Content-Type: application/zip\r\n\r\n");
        body.write(zip);
        write(body, "\r\n--" + BOUNDARY + "--\r\n");

        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/student/submissions").openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
        if (cookie != null) {
            connection.setRequestProperty("Cookie", cookie);
        }
        byte[] requestBody = body.toByteArray();
        connection.setFixedLengthStreamingMode(requestBody.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(requestBody);
        }
        try {
            int status = connection.getResponseCode();
            InputStream responseStream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            return new HttpResult(status, responseStream == null ? "" : readText(responseStream));
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] createZip() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("src/Main.java"));
            zip.write("class Main { public static void main(String[] args) {} }"
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static void write(OutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String readText(InputStream input) throws IOException {
        try (InputStream response = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = response.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            value.append(String.format("%02x", item & 0xff));
        }
        return value.toString();
    }

    private static final class HttpResult {
        private final int status;
        private final String body;

        private HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static final class TestSubmissionController extends SubmissionController {
        private final FakeSubmissionDatabase database;

        private TestSubmissionController(FakeSubmissionDatabase database) {
            this.database = database;
        }

        @Override
        protected Connection openConnection() {
            return database.openConnection();
        }
    }

    private static final class TestLoginServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            request.getSession(true).setAttribute("userId", "E2E-STUDENT");
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }

    private static final class FakeSubmissionDatabase {
        private final Map<String, Map<String, Object>> rowsByHash = new HashMap<>();

        private synchronized boolean containsHash(String hash) {
            return rowsByHash.containsKey(hash);
        }

        private Connection openConnection() {
            return proxy(Connection.class, (proxy, method, args) -> {
                if ("createStatement".equals(method.getName())) {
                    return createStatement();
                }
                if ("prepareStatement".equals(method.getName())) {
                    return prepareStatement((String) args[0]);
                }
                if ("close".equals(method.getName())) {
                    return null;
                }
                return defaultValue(method.getReturnType());
            });
        }

        private Statement createStatement() {
            return proxy(Statement.class, (proxy, method, args) -> {
                if ("execute".equals(method.getName())) {
                    return true;
                }
                return defaultValue(method.getReturnType());
            });
        }

        private PreparedStatement prepareStatement(String sql) {
            Map<Integer, Object> parameters = new HashMap<>();
            return proxy(PreparedStatement.class, (proxy, method, args) -> {
                if ("setString".equals(method.getName()) || "setTimestamp".equals(method.getName())) {
                    parameters.put((Integer) args[0], args[1]);
                    return null;
                }
                if ("executeQuery".equals(method.getName())) {
                    Map<String, Object> row;
                    synchronized (this) {
                        row = rowsByHash.get(parameters.get(1));
                    }
                    return resultSet(row);
                }
                if ("executeUpdate".equals(method.getName())) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("submission_id", parameters.get(1));
                    row.put("user_id", parameters.get(2));
                    row.put("file_url", parameters.get(3));
                    row.put("file_hash", parameters.get(4));
                    row.put("status", parameters.get(5));
                    row.put("submitted_at", parameters.get(6));
                    synchronized (this) {
                        rowsByHash.put((String) parameters.get(4), row);
                    }
                    return 1;
                }
                return defaultValue(method.getReturnType());
            });
        }

        private static ResultSet resultSet(Map<String, Object> row) {
            boolean[] visited = {false};
            return proxy(ResultSet.class, (proxy, method, args) -> {
                if ("next".equals(method.getName())) {
                    if (row == null || visited[0]) {
                        return false;
                    }
                    visited[0] = true;
                    return true;
                }
                if ("getString".equals(method.getName())) {
                    return row.get((String) args[0]);
                }
                if ("getTimestamp".equals(method.getName())) {
                    Object timestamp = row.get((String) args[0]);
                    return timestamp instanceof Timestamp ? timestamp : Timestamp.valueOf(LocalDateTime.now());
                }
                return defaultValue(method.getReturnType());
            });
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, InvocationHandler handler) {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive() || type == void.class) {
                return null;
            }
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0.0f;
            if (type == double.class) return 0.0d;
            throw new IllegalArgumentException("Unsupported primitive type: " + type);
        }
    }
}
