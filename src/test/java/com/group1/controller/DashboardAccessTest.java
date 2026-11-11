package com.group1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.group1.filter.AuthFilter;
import com.group1.model.Users;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.junit.Test;

public class DashboardAccessTest {
    @Test
    public void homeRedirectsAnonymousVisitorsToLogin() throws Exception {
        Exchange exchange = invokeHome(null, null, null);
        assertEquals("/aita/login", exchange.redirect);
        assertEquals(0, exchange.errorStatus);
    }

    @Test
    public void homeRejectsMismatchedSessionIdentity() throws Exception {
        Exchange exchange = invokeHome("Student", "student-1", "student-2");
        assertEquals(HttpServletResponse.SC_FORBIDDEN, exchange.errorStatus);
        assertNull(exchange.redirect);
    }

    @Test
    public void homeDoesNotLoadSystemWideStatisticsForStudents() throws Exception {
        Exchange exchange = invokeHome("student", "student-1", "student-1");
        assertTrue(exchange.forwarded);
        assertFalse(exchange.databaseAccessed);
        assertEquals("no-store", exchange.cacheControl);
    }

    @Test
    public void teacherDashboardRouteIsRegisteredOnItsController() throws Exception {
        WebServlet servlet = TeacherDashboardController.class.getAnnotation(WebServlet.class);
        assertTrue(Arrays.asList(servlet.value()).contains("/teacher/dashboard"));
    }

    @Test
    public void authFilterRejectsStudentsFromTeacherDashboard() throws Exception {
        for (String role : new String[] {"student", "admin"}) {
            Exchange exchange = new Exchange();
            HttpServletRequest request = request(role, role + "-1", role + "-1",
                    "/teacher/dashboard", exchange);
            FilterChain chain = proxy(FilterChain.class, (proxy, method, args) -> {
                if ("doFilter".equals(method.getName())) {
                    exchange.filterChainCalled = true;
                }
                return defaultValue(method.getReturnType());
            });

            new AuthFilter().doFilter(request, response(exchange), chain);

            assertEquals(HttpServletResponse.SC_FORBIDDEN, exchange.errorStatus);
            assertFalse(exchange.filterChainCalled);
        }
    }

    @Test(expected = ServletException.class)
    public void homeDoesNotHideDatabaseFailuresAsAnEmptyDashboard() throws Exception {
        invokeHome("Teacher", "teacher-1", "teacher-1");
    }

    @Test
    public void scoresRedirectAnonymousVisitorsToLogin() throws Exception {
        Exchange exchange = invokeScores(null, null, null);
        assertEquals("/aita/login", exchange.redirect);
        assertEquals(0, exchange.errorStatus);
    }

    @Test
    public void scoresRejectStudentBeforeLoadingAllScores() throws Exception {
        Exchange exchange = invokeScores("Student", "student-1", "student-1");
        assertEquals(HttpServletResponse.SC_FORBIDDEN, exchange.errorStatus);
        assertFalse(exchange.forwarded);
        assertFalse(exchange.databaseAccessed);
    }

    @Test
    public void teachersAndAdminsCanViewScoresWithoutEnablingStudentAccess() throws Exception {
        for (String role : new String[] {"Teacher", "Admin"}) {
            Exchange exchange = invokeScores(role, role.toLowerCase() + "-1", role.toLowerCase() + "-1");
            assertTrue(exchange.forwarded);
            assertTrue(exchange.databaseAccessed);
            assertEquals("no-store", exchange.cacheControl);
        }
    }

    private static Exchange invokeHome(String role, String userId, String sessionUserId) throws Exception {
        return invokeHome(role, userId, sessionUserId, "/home");
    }

    private static Exchange invokeHome(String role, String userId, String sessionUserId, String servletPath)
            throws Exception {
        Exchange exchange = new Exchange();
        HttpServletRequest request = request(role, userId, sessionUserId, servletPath, exchange);
        HttpServletResponse response = response(exchange);
        new TestHomeController(exchange).handleGet(request, response);
        return exchange;
    }

    private static Exchange invokeScores(String role, String userId, String sessionUserId) throws Exception {
        Exchange exchange = new Exchange();
        HttpServletRequest request = request(role, userId, sessionUserId, "/scores", exchange);
        HttpServletResponse response = response(exchange);
        new TestScoreController(exchange).handleGet(request, response);
        return exchange;
    }

    private static HttpServletRequest request(String role, String userId, String sessionUserId,
            String servletPath, Exchange exchange) {
        HttpSession session = null;
        if (role != null) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("currentUser", new Users(userId, "user", "", "", "", role, ""));
            attributes.put("userId", sessionUserId);
            session = proxy(HttpSession.class, (proxy, method, args) -> {
                if ("getAttribute".equals(method.getName())) {
                    return attributes.get(args[0]);
                }
                return defaultValue(method.getReturnType());
            });
        }

        HttpSession requestSession = session;
        return proxy(HttpServletRequest.class, (proxy, method, args) -> {
            if ("getSession".equals(method.getName())) {
                return requestSession;
            }
            if ("getContextPath".equals(method.getName())) {
                return "/aita";
            }
            if ("getServletPath".equals(method.getName())) {
                return servletPath;
            }
            if ("getRequestDispatcher".equals(method.getName())) {
                return proxy(javax.servlet.RequestDispatcher.class, (dispatcher, dispatchMethod, dispatchArgs) -> {
                    if ("forward".equals(dispatchMethod.getName())) {
                        exchange.forwarded = true;
                    }
                    return defaultValue(dispatchMethod.getReturnType());
                });
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static HttpServletResponse response(Exchange exchange) {
        return proxy(HttpServletResponse.class, (proxy, method, args) -> {
            if ("sendRedirect".equals(method.getName())) {
                exchange.redirect = (String) args[0];
            } else if ("sendError".equals(method.getName())) {
                exchange.errorStatus = (Integer) args[0];
            } else if ("setHeader".equals(method.getName()) && "Cache-Control".equals(args[0])) {
                exchange.cacheControl = (String) args[1];
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static final class TestHomeController extends HomeController {
        private final Exchange exchange;

        private TestHomeController(Exchange exchange) {
            this.exchange = exchange;
        }

        private void handleGet(HttpServletRequest request, HttpServletResponse response) throws Exception {
            doGet(request, response);
        }

        @Override
        protected java.sql.Connection openConnection() throws java.sql.SQLException {
            exchange.databaseAccessed = true;
            throw new java.sql.SQLException("Student dashboard must not load system-wide statistics.");
        }
    }

    private static final class TestScoreController extends ScoreController {
        private final Exchange exchange;

        private TestScoreController(Exchange exchange) {
            this.exchange = exchange;
        }

        private void handleGet(HttpServletRequest request, HttpServletResponse response) throws Exception {
            doGet(request, response);
        }

        @Override
        protected Connection openConnection() {
            exchange.databaseAccessed = true;
            return proxy(Connection.class, (connection, method, args) -> {
                if ("createStatement".equals(method.getName())) {
                    return proxy(Statement.class, (statement, statementMethod, statementArgs) ->
                            defaultValue(statementMethod.getReturnType()));
                }
                if ("prepareStatement".equals(method.getName())) {
                    return proxy(PreparedStatement.class, (statement, statementMethod, statementArgs) -> {
                        if ("executeQuery".equals(statementMethod.getName())) {
                            return proxy(ResultSet.class, (resultSet, resultMethod, resultArgs) -> {
                                if ("next".equals(resultMethod.getName())) {
                                    return false;
                                }
                                return defaultValue(resultMethod.getReturnType());
                            });
                        }
                        return defaultValue(statementMethod.getReturnType());
                    });
                }
                return defaultValue(method.getReturnType());
            });
        }
    }

    private static final class Exchange {
        private String redirect;
        private int errorStatus;
        private boolean forwarded;
        private boolean databaseAccessed;
        private boolean filterChainCalled;
        private String cacheControl;
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
