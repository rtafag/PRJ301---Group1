package com.group1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.group1.filter.SubmissionSecurityFilter;
import com.group1.model.AnalysisResult;
import com.group1.model.DashboardFinding;
import com.group1.model.Submission;
import com.group1.model.Users;
import javax.servlet.DispatcherType;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.servlet.FilterHolder;
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
    private TestSubmissionController submissionServlet;
    private TestAnalysisController analysisServlet;
    private TestTeacherDashboardController teacherDashboardServlet;
    private String baseUrl;

    @Before
    public void startServletContainer() throws Exception {
        database = new FakeSubmissionDatabase();
        server = new Server(0);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        context.addFilter(new FilterHolder(new SubmissionSecurityFilter()), "/student/*",
                EnumSet.of(DispatcherType.REQUEST));
        context.addFilter(new FilterHolder(new SubmissionSecurityFilter()), "/teacher/*",
                EnumSet.of(DispatcherType.REQUEST));

        submissionServlet = new TestSubmissionController(database);
        ServletHolder submission = new ServletHolder(submissionServlet);
        context.addServlet(submission, "/student/submissions");
        analysisServlet = new TestAnalysisController(database);
        ServletHolder analysis = new ServletHolder(analysisServlet);
        context.addServlet(analysis, "/student/analysis");
        analysis.getRegistration().addMapping("/teacher/analysis");
        context.addServlet(new ServletHolder(new TestAnalysisViewServlet()), "/WEB-INF/views/analysis-dashboard.jsp");
        teacherDashboardServlet = new TestTeacherDashboardController(database);
        context.addServlet(new ServletHolder(teacherDashboardServlet), "/teacher/dashboard");
        context.addServlet(new ServletHolder(new TestTeacherDashboardViewServlet()),
                "/WEB-INF/views/teacher-dashboard.jsp");
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
        assertEquals(0, submissionServlet.getPostInvocations());

        HttpResult accepted = upload(zip, cookie);
        assertEquals(HttpServletResponse.SC_CREATED, accepted.status);
        String expectedHash = hex(MessageDigest.getInstance("SHA-256").digest(zip));
        assertTrue(accepted.body.contains("\"sha256\":\"" + expectedHash + "\""));
        assertTrue(database.containsHash(expectedHash));

        HttpResult duplicate = upload(zip, cookie);
        assertEquals(HttpServletResponse.SC_CONFLICT, duplicate.status);
    }

    @Test
    public void submissionFilterRejectsAuthenticatedNonStudents() throws Exception {
        String teacherCookie = loginAndGetSessionCookie("Teacher");

        HttpResult forbidden = upload(createZip(), teacherCookie);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, forbidden.status);
        assertEquals(0, submissionServlet.getPostInvocations());
        assertTrue(database.isEmpty());
    }

    @Test
    public void submissionFilterRejectsNonZipBeforeServletAndAddsSha256ForValidZip() throws Exception {
        String cookie = loginAndGetSessionCookie();

        HttpResult rejected = upload(createZip(), cookie, "project.txt");
        assertEquals(HttpServletResponse.SC_BAD_REQUEST, rejected.status);
        assertEquals(0, submissionServlet.getPostInvocations());

        HttpResult accepted = upload(createZip(), cookie, "project.zip");
        assertEquals(HttpServletResponse.SC_CREATED, accepted.status);
        assertEquals(1, submissionServlet.getPostInvocations());
        assertTrue(accepted.body.contains("\"sha256\":\""
                + hex(MessageDigest.getInstance("SHA-256").digest(createZip())) + "\""));
    }

    @Test
    public void analysisDashboardShowsAstFindingsAndSuggestionsToSubmissionOwner() throws Exception {
        String cookie = loginAndGetSessionCookie();

        HttpResult result = getAnalysis("OWNED", cookie);

        assertEquals(HttpServletResponse.SC_OK, result.status);
        assertTrue(result.body.contains("SUBMISSION=OWNED"));
        assertTrue(result.body.contains("RULE=Null check"));
        assertTrue(result.body.contains("SEVERITY=High"));
        assertTrue(result.body.contains("LOCATION=src/Main.java:12"));
        assertTrue(result.body.contains("SUGGESTION=Check for null before calling the method."));
        assertTrue(result.body.contains("AST={\"nodes\":[]}"));
    }

    @Test
    public void analysisDashboardHidesOtherUsersSubmissionsAndPendingResults() throws Exception {
        String cookie = loginAndGetSessionCookie();

        HttpResult foreignSubmission = getAnalysis("OTHER", cookie);
        HttpResult pendingAnalysis = getAnalysis("PENDING", cookie);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, foreignSubmission.status);
        assertEquals(HttpServletResponse.SC_OK, pendingAnalysis.status);
        assertTrue(pendingAnalysis.body.contains("NO_ANALYSIS"));
    }

    @Test
    public void analysisDashboardRequiresStudentSession() throws Exception {
        HttpResult anonymous = getAnalysis("OWNED", null);
        String teacherCookie = loginAndGetSessionCookie("Teacher");
        HttpResult teacherOnStudentRoute = getAnalysis("OWNED", teacherCookie, false);
        HttpResult studentOnTeacherRoute = getAnalysis("OWNED", loginAndGetSessionCookie(), true);

        assertEquals(HttpServletResponse.SC_FOUND, anonymous.status);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, teacherOnStudentRoute.status);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, studentOnTeacherRoute.status);
        assertEquals(0, analysisServlet.getGetInvocations());
    }

    @Test
    public void teacherCanReviewClassSubmissionsAndOtherStudentsAnalysis() throws Exception {
        String teacherCookie = loginAndGetSessionCookie("Teacher");

        HttpResult submissions = getTeacherDashboard(teacherCookie);
        HttpResult analysis = getAnalysis("OTHER", teacherCookie, true);

        assertEquals(HttpServletResponse.SC_OK, submissions.status);
        assertTrue(submissions.body.contains("SUBMISSION=OTHER"));
        assertEquals(HttpServletResponse.SC_OK, analysis.status);
        assertTrue(analysis.body.contains("SUBMISSION=OTHER"));
        assertTrue(analysis.body.contains("RULE=Null check"));
    }

    private String loginAndGetSessionCookie() throws IOException {
        return loginAndGetSessionCookie("Student");
    }

    private HttpResult getAnalysis(String submissionId, String cookie) throws IOException {
        return getAnalysis(submissionId, cookie, false);
    }

    private HttpResult getAnalysis(String submissionId, String cookie, boolean teacherRoute) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl
                + (teacherRoute ? "/teacher/analysis" : "/student/analysis")
                + "?submissionId=" + submissionId).openConnection();
        connection.setInstanceFollowRedirects(false);
        if (cookie != null) {
            connection.setRequestProperty("Cookie", cookie);
        }
        try {
            int status = connection.getResponseCode();
            InputStream responseStream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            return new HttpResult(status, responseStream == null ? "" : readText(responseStream));
        } finally {
            connection.disconnect();
        }
    }

    private HttpResult getTeacherDashboard(String cookie) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + "/teacher/dashboard").openConnection();
        connection.setRequestProperty("Cookie", cookie);
        try {
            int status = connection.getResponseCode();
            InputStream responseStream = status < 400 ? connection.getInputStream() : connection.getErrorStream();
            return new HttpResult(status, responseStream == null ? "" : readText(responseStream));
        } finally {
            connection.disconnect();
        }
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

    private HttpResult upload(byte[] zip, String cookie) throws IOException {
        return upload(zip, cookie, "project.zip");
    }

    private HttpResult upload(byte[] zip, String cookie, String fileName) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        write(body, "--" + BOUNDARY + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"submissionFile\"; filename=\""
                + fileName + "\"\r\n");
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
        private int postInvocations;

        private TestSubmissionController(FakeSubmissionDatabase database) {
            this.database = database;
        }

        @Override
        protected Connection openConnection() {
            return database.openConnection();
        }

        @Override
        protected synchronized void doPost(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            postInvocations++;
            super.doPost(request, response);
        }

        private synchronized int getPostInvocations() {
            return postInvocations;
        }
    }

    private static final class TestAnalysisController extends AnalysisDashboardController {
        private final FakeSubmissionDatabase database;
        private int getInvocations;

        private TestAnalysisController(FakeSubmissionDatabase database) {
            this.database = database;
        }

        @Override
        protected Connection openConnection() {
            return database.openConnection();
        }

        @Override
        protected synchronized void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            getInvocations++;
            super.doGet(request, response);
        }

        private synchronized int getGetInvocations() {
            return getInvocations;
        }
    }

    private static final class TestAnalysisViewServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        @SuppressWarnings("unchecked")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain");
            AnalysisResult analysis = (AnalysisResult) request.getAttribute("analysis");
            Submission submission = (Submission) request.getAttribute("submission");
            response.getWriter().println("SUBMISSION=" + submission.getSubmissionId());
            if (analysis == null) {
                response.getWriter().println("NO_ANALYSIS");
                return;
            }
            response.getWriter().println("AST=" + analysis.getAstJson());
            for (DashboardFinding finding : (java.util.List<DashboardFinding>) request.getAttribute("findings")) {
                response.getWriter().println("RULE=" + finding.getRuleName());
                response.getWriter().println("SEVERITY=" + finding.getSeverity());
                response.getWriter().println("LOCATION=" + finding.getFileName() + ":" + finding.getLineNumber());
                response.getWriter().println("SUGGESTION=" + finding.getSuggestedFix());
            }
        }
    }

    private static final class TestTeacherDashboardController extends TeacherDashboardController {
        private final FakeSubmissionDatabase database;

        private TestTeacherDashboardController(FakeSubmissionDatabase database) {
            this.database = database;
        }

        @Override
        protected Connection openConnection() {
            return database.openConnection();
        }
    }

    private static final class TestTeacherDashboardViewServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        @SuppressWarnings("unchecked")
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain");
            for (Submission submission : (java.util.List<Submission>) request.getAttribute("submissions")) {
                response.getWriter().println("SUBMISSION=" + submission.getSubmissionId());
                response.getWriter().println("OWNER=" + submission.getUserId());
            }
        }
    }

    private static final class TestLoginServlet extends HttpServlet {
        private static final long serialVersionUID = 1L;

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws IOException, ServletException {
            request.getSession(true).setAttribute("userId", "E2E-STUDENT");
            String role = request.getParameter("role");
            request.getSession(false).setAttribute("currentUser",
                    new Users("E2E-STUDENT", "e2e", "", "E2E Student", "", role, ""));
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        }
    }

    private static final class FakeSubmissionDatabase {
        private final Map<String, Map<String, Object>> rowsByHash = new HashMap<>();

        private synchronized boolean containsHash(String hash) {
            return rowsByHash.containsKey(hash);
        }

        private synchronized boolean isEmpty() {
            return rowsByHash.isEmpty();
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
                if ("setString".equals(method.getName()) || "setTimestamp".equals(method.getName())
                        || "setInt".equals(method.getName())) {
                    parameters.put((Integer) args[0], args[1]);
                    return null;
                }
                if ("executeQuery".equals(method.getName())) {
                    java.util.List<Map<String, Object>> rows;
                    synchronized (this) {
                        rows = queryRows(sql, parameters.get(1));
                    }
                    return resultSet(rows);
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

        private java.util.List<Map<String, Object>> queryRows(String sql, Object parameter) {
            java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
            if (sql.contains("FROM Submission WHERE submission_id")) {
                if ("OWNED".equals(parameter)) {
                    rows.add(submissionRow("OWNED", "E2E-STUDENT"));
                } else if ("OTHER".equals(parameter)) {
                    rows.add(submissionRow("OTHER", "ANOTHER-STUDENT"));
                } else if ("PENDING".equals(parameter)) {
                    rows.add(submissionRow("PENDING", "E2E-STUDENT"));
                }
            } else if (sql.contains("FROM AnalysisResult WHERE submission_id")) {
                if ("OWNED".equals(parameter) || "OTHER".equals(parameter)) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("analysis_id", 7);
                    row.put("submission_id", parameter);
                    row.put("ast_json", "{\"nodes\":[]}");
                    row.put("ai_feedback", "Review the finding.");
                    row.put("execution_time_ms", 42);
                    rows.add(row);
                }
            } else if (sql.contains("FROM RuleViolation WHERE analysis_id")) {
                if (Integer.valueOf(7).equals(parameter)) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("violation_id", 1);
                    row.put("analysis_id", 7);
                    row.put("rule_id", 3);
                    row.put("file_name", "src/Main.java");
                    row.put("line_number", 12);
                    row.put("suggested_fix", "Check for null before calling the method.");
                    rows.add(row);
                }
            } else if (sql.contains("FROM RuleDefinition ORDER BY")) {
                Map<String, Object> row = new HashMap<>();
                row.put("rule_id", 3);
                row.put("rule_code", "NULL_CHECK");
                row.put("rule_name", "Null check");
                row.put("severity", "High");
                row.put("penalty_points", 2.0f);
                row.put("description", "Possible null dereference.");
                rows.add(row);
            } else if (sql.contains("FROM Submission WHERE file_hash")) {
                Map<String, Object> row = rowsByHash.get(parameter);
                if (row != null) {
                    rows.add(row);
                }
            } else if (sql.contains("FROM Submission") && sql.contains("ORDER BY submitted_at DESC")) {
                rows.add(submissionRow("OWNED", "E2E-STUDENT"));
                rows.add(submissionRow("OTHER", "ANOTHER-STUDENT"));
                rows.add(submissionRow("PENDING", "E2E-STUDENT"));
            }
            return rows;
        }

        private static Map<String, Object> submissionRow(String id, String userId) {
            Map<String, Object> row = new HashMap<>();
            row.put("submission_id", id);
            row.put("user_id", userId);
            row.put("file_url", "");
            row.put("file_hash", "");
            row.put("status", "Submitted");
            row.put("submitted_at", Timestamp.valueOf(LocalDateTime.now()));
            return row;
        }

        private static ResultSet resultSet(java.util.List<Map<String, Object>> rows) {
            int[] current = {-1};
            return proxy(ResultSet.class, (proxy, method, args) -> {
                if ("next".equals(method.getName())) {
                    if (current[0] + 1 >= rows.size()) {
                        return false;
                    }
                    current[0]++;
                    return true;
                }
                Map<String, Object> row = current[0] < 0 ? null : rows.get(current[0]);
                if ("getString".equals(method.getName())) {
                    return row.get((String) args[0]);
                }
                if ("getTimestamp".equals(method.getName())) {
                    Object timestamp = row.get((String) args[0]);
                    return timestamp instanceof Timestamp ? timestamp : Timestamp.valueOf(LocalDateTime.now());
                }
                if ("getInt".equals(method.getName())) {
                    Object value = row.get(args[0] instanceof String ? args[0] : "");
                    return value instanceof Number ? ((Number) value).intValue() : 0;
                }
                if ("getFloat".equals(method.getName())) {
                    Object value = row.get(args[0] instanceof String ? args[0] : "");
                    return value instanceof Number ? ((Number) value).floatValue() : 0f;
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
