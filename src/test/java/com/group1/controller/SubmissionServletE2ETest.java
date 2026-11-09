package com.group1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.group1.filter.AuthFilter;
import com.group1.filter.Sha256Filter;
import com.group1.model.Users;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.DispatcherType;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.FilterHolder;
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
        context.addFilter(new FilterHolder(new AuthFilter()), "/student/submissions",
                EnumSet.of(DispatcherType.REQUEST));
        context.addFilter(new FilterHolder(new Sha256Filter()), "/student/submissions",
                EnumSet.of(DispatcherType.REQUEST));

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
        for (String filePath : database.savedFiles()) {
            Files.deleteIfExists(Paths.get(filePath));
        }
    }

    @Test
    public void submissionServletHashesPersistsAndRejectsDuplicateZipUploads() throws Exception {
        byte[] zip = createZip();
        String cookie = loginAndGetSessionCookie("Student");
        String csrfToken = getCsrfToken(cookie);

        HttpResult unauthorized = upload(zip, null);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, unauthorized.status);

        HttpResult accepted = upload(zip, cookie, csrfToken);
        assertEquals(HttpServletResponse.SC_CREATED, accepted.status);
        String expectedHash = hex(MessageDigest.getInstance("SHA-256").digest(zip));
        assertTrue(accepted.body.contains("\"sha256\":\"" + expectedHash + "\""));
        assertTrue(database.containsHash(expectedHash));
        assertTrue(java.util.Arrays.equals(zip, Files.readAllBytes(Paths.get(database.filePath(expectedHash)))));

        HttpResult duplicate = upload(zip, cookie, csrfToken);
        assertEquals(HttpServletResponse.SC_CONFLICT, duplicate.status);
    }

    @Test
    public void submissionFilterRejectsTeachersAndInvalidCsrfTokens() throws Exception {
        byte[] zip = createZip();
        String hash = hex(MessageDigest.getInstance("SHA-256").digest(zip));

        for (String role : new String[] {"Teacher", "Admin"}) {
            String cookie = loginAndGetSessionCookie(role);
            assertEquals(HttpServletResponse.SC_FORBIDDEN, upload(zip, cookie, null).status);
        }

        String studentCookie = loginAndGetSessionCookie("Student");
        String csrfToken = getCsrfToken(studentCookie);
        assertTrue(csrfToken.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, upload(zip, studentCookie, "invalid-token").status);
        assertFalse("The security filter must reject invalid requests before storing them.", database.containsHash(hash));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, upload(zip, studentCookie, null).status);
    }

    @Test
    public void submissionServletRejectsMissingCsrfAndInvalidZipArchives() throws Exception {
        String cookie = loginAndGetSessionCookie("Student");
        String csrfToken = getCsrfToken(cookie);
        byte[] invalidZip = "not a ZIP archive".getBytes(StandardCharsets.UTF_8);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, upload(createZip(), cookie, null).status);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, upload(invalidZip, cookie, csrfToken).status);
        assertFalse(database.containsHash(hex(MessageDigest.getInstance("SHA-256").digest(invalidZip))));
    }

    @Test
    public void submissionServletRejectsUnsafeArchivesAndNonZipFiles() throws Exception {
        String cookie = loginAndGetSessionCookie("Student");
        String csrfToken = getCsrfToken(cookie);

        HttpResult wrongExtension = upload(createZip(), cookie, "project.jar", csrfToken);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, wrongExtension.status);

        HttpResult unsafeArchive = upload(createZip("../outside.txt"), cookie, "project.zip", csrfToken);
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, unsafeArchive.status);
        assertTrue(database.savedFiles().isEmpty());
    }

    @Test
    public void authFilterRedirectsAnonymousPortalGetAndCreatesCsrfTokenForStudents() throws Exception {
        HttpResult anonymous = getSubmissionPage(null);
        assertEquals(HttpServletResponse.SC_FOUND, anonymous.status);

        String cookie = loginAndGetSessionCookie("Student");
        String csrfToken = getCsrfToken(cookie);
        assertTrue(csrfToken.matches("[A-Za-z0-9_-]{43}"));
    }

    private String loginAndGetSessionCookie() throws IOException {
        return loginAndGetSessionCookie("Student");
    }

    private String loginAndGetSessionCookie(String role) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/test-login?role=" + role).openConnection();
        try {
            assertEquals(HttpServletResponse.SC_NO_CONTENT, connection.getResponseCode());
            String setCookie = connection.getHeaderField("Set-Cookie");
            assertTrue("The login endpoint must establish an HTTP session.", setCookie != null);
            return setCookie.substring(0, setCookie.indexOf(';'));
        } finally {
            connection.disconnect();
        }
    }

    private String getCsrfToken(String cookie) throws IOException {
        HttpResult result = getSubmissionPage(cookie);
        assertEquals(HttpServletResponse.SC_OK, result.status);
        return result.body;
    }

    private HttpResult getSubmissionPage(String cookie) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/student/submissions").openConnection();
        connection.setInstanceFollowRedirects(false);
        if (cookie != null) {
            connection.setRequestProperty("Cookie", cookie);
        }
        try {
            int status = connection.getResponseCode();
            InputStream responseStream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            String body = responseStream == null ? "" : readText(responseStream);
            return new HttpResult(status, body);
        } finally {
            connection.disconnect();
        }
    }

    private HttpResult upload(byte[] zip, String cookie) throws IOException {
        return upload(zip, cookie, null);
    }

    private HttpResult upload(byte[] zip, String cookie, String csrfToken) throws IOException {
        return upload(zip, cookie, "project.zip", csrfToken);
    }

    private HttpResult upload(byte[] zip, String cookie, String fileName, String csrfToken) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write(body, "--" + BOUNDARY + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"submissionFile\"; filename=\"" + fileName + "\"\r\n");
        write(body, "Content-Type: application/zip\r\n\r\n");
        body.write(zip);
        write(body, "\r\n--" + BOUNDARY + "--\r\n");

        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/student/submissions").openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
        if (csrfToken != null) {
            connection.setRequestProperty("X-CSRF-Token", csrfToken);
        }
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
        return createZip("src/Main.java");
    }

    private static byte[] createZip(String entryName) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(entryName));
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

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException {
            response.setContentType("text/plain");
            response.getWriter().write((String) request.getAttribute(AuthFilter.CSRF_SESSION_ATTRIBUTE));
        }
    }

    private static final class TestLoginServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            String role = request.getParameter("role");
            if (role == null) {
                role = "Student";
            }
            HttpSession session = request.getSession(true);
            String userId = "E2E-" + role.toUpperCase();
            session.setAttribute("currentUser", new Users(userId, "test", "", "", "", role, ""));
            session.setAttribute("userId", userId);
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }

    private static final class FakeSubmissionDatabase {
        private final Map<String, Map<String, Object>> rowsByHash = new HashMap<>();

        private synchronized boolean containsHash(String hash) {
            return rowsByHash.containsKey(hash);
        }

        private synchronized String filePath(String hash) {
            return (String) rowsByHash.get(hash).get("file_url");
        }

        private synchronized Collection<String> savedFiles() {
            Collection<String> paths = new java.util.ArrayList<>();
            for (Map<String, Object> row : rowsByHash.values()) {
                paths.add((String) row.get("file_url"));
            }
            return paths;
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
