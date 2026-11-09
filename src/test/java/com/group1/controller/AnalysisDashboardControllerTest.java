package com.group1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.group1.model.AnalysisDashboardItem;
import com.group1.model.Users;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.Test;

public class AnalysisDashboardControllerTest {
    @Test
    public void studentSeesOnlyTheirAnalysisAndSuggestedFixes() throws Exception {
        DashboardDatabase database = new DashboardDatabase();
        Exchange exchange = invoke("Student", "student-1", "student-1", database);

        assertNull(exchange.redirect);
        assertEquals(0, exchange.errorStatus);
        assertTrue(exchange.forwarded);
        assertEquals("/WEB-INF/views/analysis-dashboard.jsp", exchange.dispatcherPath);
        assertTrue(database.query.contains("WHERE s.user_id = ?"));
        assertEquals("student-1", database.userFilter);

        @SuppressWarnings("unchecked")
        List<AnalysisDashboardItem> items =
                (List<AnalysisDashboardItem>) exchange.attributes.get("analyses");
        assertEquals(1, items.size());
        assertEquals("SUB-STUDENT-1", items.get(0).getSubmissionId());
        assertEquals(2, items.get(0).getFindings().size());
        assertEquals("Critical", items.get(0).getFindings().get(0).getSeverity());
        assertEquals("Use a null check.", items.get(0).getFindings().get(0).getSuggestedFix());
    }

    @Test
    public void teacherSeesAllAnalysisResults() throws Exception {
        DashboardDatabase database = new DashboardDatabase();
        Exchange exchange = invoke("Teacher", "teacher-1", "teacher-1", database);

        assertNull(exchange.redirect);
        assertEquals(0, exchange.errorStatus);
        assertTrue(exchange.forwarded);
        assertFalse(database.query.contains("WHERE s.user_id = ?"));
        assertNull(database.userFilter);

        @SuppressWarnings("unchecked")
        List<AnalysisDashboardItem> items =
                (List<AnalysisDashboardItem>) exchange.attributes.get("analyses");
        assertEquals(2, items.size());
        assertEquals("all", exchange.attributes.get("dashboardScope"));
    }

    @Test
    public void adminSeesAllAnalysisResults() throws Exception {
        DashboardDatabase database = new DashboardDatabase();
        Exchange exchange = invoke("Admin", "admin-1", "admin-1", database);

        assertNull(exchange.redirect);
        assertEquals(0, exchange.errorStatus);
        assertTrue(exchange.forwarded);
        assertFalse(database.query.contains("WHERE s.user_id = ?"));
        assertNull(database.userFilter);
        assertEquals("all", exchange.attributes.get("dashboardScope"));
    }

    @Test
    public void unsupportedRoleCannotViewDashboard() throws Exception {
        Exchange exchange = invoke("Guest", "guest-1", "guest-1", new DashboardDatabase());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, exchange.errorStatus);
        assertFalse(exchange.forwarded);
    }

    @Test
    public void anonymousUserIsRedirectedToLogin() throws Exception {
        Exchange exchange = invoke(null, null, null, new DashboardDatabase());
        assertEquals("/aita/login", exchange.redirect);
        assertFalse(exchange.forwarded);
    }

    @Test
    public void mismatchedSessionUserIsRejected() throws Exception {
        Exchange exchange = invoke("Student", "student-1", "student-2", new DashboardDatabase());
        assertEquals(HttpServletResponse.SC_FORBIDDEN, exchange.errorStatus);
        assertFalse(exchange.forwarded);
    }

    @Test(expected = ServletException.class)
    public void databaseSchemaFailuresAreNotSilenced() throws Exception {
        DashboardDatabase database = new DashboardDatabase();
        database.failSchemaInitialization = true;
        invoke("Student", "student-1", "student-1", database);
    }

    private static Exchange invoke(String role, String userId, String sessionUserId,
            DashboardDatabase database) throws Exception {
        Exchange exchange = new Exchange();
        HttpSession session = null;
        if (role != null) {
            Map<String, Object> sessionAttributes = new HashMap<>();
            sessionAttributes.put("currentUser", new Users(userId, "user", "", "", "", role, ""));
            sessionAttributes.put("userId", sessionUserId);
            session = proxy(HttpSession.class, (proxy, method, args) -> {
                if ("getAttribute".equals(method.getName())) {
                    return sessionAttributes.get(args[0]);
                }
                if ("setAttribute".equals(method.getName())) {
                    sessionAttributes.put((String) args[0], args[1]);
                    return null;
                }
                return defaultValue(method.getReturnType());
            });
        }

        HttpSession requestSession = session;
        HttpServletRequest request = proxy(HttpServletRequest.class, (proxy, method, args) -> {
            if ("getSession".equals(method.getName())) {
                return requestSession;
            }
            if ("getContextPath".equals(method.getName())) {
                return "/aita";
            }
            if ("getRequestDispatcher".equals(method.getName())) {
                exchange.dispatcherPath = (String) args[0];
                return proxy(RequestDispatcher.class, (dispatcher, dispatchMethod, dispatchArgs) -> {
                    if ("forward".equals(dispatchMethod.getName())) {
                        exchange.forwarded = true;
                    }
                    return defaultValue(dispatchMethod.getReturnType());
                });
            }
            if ("setAttribute".equals(method.getName())) {
                exchange.attributes.put((String) args[0], args[1]);
                return null;
            }
            if ("getAttribute".equals(method.getName())) {
                return exchange.attributes.get(args[0]);
            }
            return defaultValue(method.getReturnType());
        });

        HttpServletResponse response = proxy(HttpServletResponse.class, (proxy, method, args) -> {
            if ("sendRedirect".equals(method.getName())) {
                exchange.redirect = (String) args[0];
            } else if ("sendError".equals(method.getName())) {
                exchange.errorStatus = (Integer) args[0];
            }
            return defaultValue(method.getReturnType());
        });

        TestController controller = new TestController(database);
        controller.handleGet(request, response);
        return exchange;
    }

    private static final class TestController extends AnalysisDashboardController {
        private final DashboardDatabase database;

        private TestController(DashboardDatabase database) {
            this.database = database;
        }

        @Override
        protected Connection openConnection() {
            return database.openConnection();
        }

        private void handleGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, java.io.IOException {
            doGet(request, response);
        }
    }

    private static final class Exchange {
        private final Map<String, Object> attributes = new HashMap<>();
        private String redirect;
        private String dispatcherPath;
        private boolean forwarded;
        private int errorStatus;
    }

    private static final class DashboardDatabase {
        private final List<Map<String, Object>> rows = Arrays.asList(
                row(1, "SUB-STUDENT-1", "student-1", 101, "Critical"),
                row(1, "SUB-STUDENT-1", "student-1", 102, "High"),
                row(2, "SUB-STUDENT-2", "student-2", 202, "Warning"));
        private String query = "";
        private String userFilter;
        private boolean failSchemaInitialization;

        private Connection openConnection() {
            return proxy(Connection.class, (proxy, method, args) -> {
                if ("createStatement".equals(method.getName())) {
                    return proxy(Statement.class, (statement, statementMethod, statementArgs) -> {
                        if ("execute".equals(statementMethod.getName())) {
                            if (failSchemaInitialization) {
                                throw new java.sql.SQLException("Schema initialization failed.");
                            }
                            return true;
                        }
                        return defaultValue(statementMethod.getReturnType());
                    });
                }
                if ("prepareStatement".equals(method.getName())) {
                    query = (String) args[0];
                    return preparedStatement();
                }
                return defaultValue(method.getReturnType());
            });
        }

        private PreparedStatement preparedStatement() {
            return proxy(PreparedStatement.class, (proxy, method, args) -> {
                if ("setString".equals(method.getName())) {
                    userFilter = (String) args[1];
                    return null;
                }
                if ("executeQuery".equals(method.getName())) {
                    List<Map<String, Object>> resultRows = new ArrayList<>();
                    for (Map<String, Object> row : rows) {
                        if (userFilter == null || userFilter.equals(row.get("user_id"))) {
                            resultRows.add(row);
                        }
                    }
                    return resultSet(resultRows);
                }
                return defaultValue(method.getReturnType());
            });
        }

        private static Map<String, Object> row(int analysisId, String submissionId, String userId,
                int violationId, String severity) {
            Map<String, Object> row = new HashMap<>();
            row.put("analysis_id", analysisId);
            row.put("submission_id", submissionId);
            row.put("user_id", userId);
            row.put("ai_feedback", "Review the findings.");
            row.put("execution_time_ms", 42);
            row.put("violation_id", violationId);
            row.put("file_name", "Main.java");
            row.put("line_number", 12);
            row.put("suggested_fix", "Critical".equals(severity) ? "Use a null check." : "Review the loop.");
            row.put("rule_code", "RULE_TEST");
            row.put("rule_name", "Test finding");
            row.put("severity", severity);
            row.put("penalty_points", 1.5f);
            row.put("description", "A test description.");
            return row;
        }

        private static ResultSet resultSet(List<Map<String, Object>> rows) {
            int[] index = {-1};
            return proxy(ResultSet.class, (proxy, method, args) -> {
                if ("next".equals(method.getName())) {
                    index[0]++;
                    return index[0] < rows.size();
                }
                if ("getString".equals(method.getName())) {
                    Object value = rows.get(index[0]).get(args[0]);
                    return value == null ? null : value.toString();
                }
                if ("getInt".equals(method.getName())) {
                    Object value = rows.get(index[0]).get(args[0]);
                    return value == null ? 0 : ((Number) value).intValue();
                }
                if ("getFloat".equals(method.getName())) {
                    Object value = rows.get(index[0]).get(args[0]);
                    return value == null ? 0.0f : ((Number) value).floatValue();
                }
                if ("getObject".equals(method.getName())) {
                    return rows.get(index[0]).get(args[0]);
                }
                return defaultValue(method.getReturnType());
            });
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
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
