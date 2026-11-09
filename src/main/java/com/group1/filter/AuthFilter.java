package com.group1.filter;

import com.group1.model.Users;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebFilter(filterName = "authFilter")
public class AuthFilter implements Filter {
    public static final String CSRF_SESSION_ATTRIBUTE = "submissionCsrfToken";
    static final String AUTHENTICATED_STUDENT_ATTRIBUTE = "authenticatedStudent";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(javax.servlet.ServletRequest servletRequest,
            javax.servlet.ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        HttpSession session = request.getSession(false);
        Object sessionUser = session == null ? null : session.getAttribute("currentUser");
        Object sessionUserId = session == null ? null : session.getAttribute("userId");

        if (!(sessionUser instanceof Users) || !(sessionUserId instanceof String)) {
            rejectUnauthenticated(request, response);
            return;
        }

        Users user = (Users) sessionUser;
        String userId = ((String) sessionUserId).trim();
        String accountUserId = user.getUserId();
        if (userId.isEmpty() || accountUserId == null || !userId.equals(accountUserId.trim())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "The signed-in user could not be verified.");
            return;
        }

        String role = user.getRole() == null ? "" : user.getRole().trim().toLowerCase(Locale.ROOT);
        if (!"student".equals(role) && !"teacher".equals(role) && !"admin".equals(role)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "This account cannot access the requested page.");
            return;
        }

        String path = request.getServletPath();
        if ("/student/submissions".equals(path) && !"student".equals(role)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Only students may access the submission portal.");
            return;
        }
        if ("/scores".equals(path) && "student".equals(role)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Only teachers may view all scores.");
            return;
        }

        response.setHeader("Cache-Control", "no-store");
        if ("/student/submissions".equals(path)) {
            if ("GET".equalsIgnoreCase(request.getMethod())) {
                Object storedToken = session.getAttribute(CSRF_SESSION_ATTRIBUTE);
                String csrfToken = storedToken instanceof String ? (String) storedToken : null;
                if (csrfToken == null || csrfToken.isEmpty()) {
                    csrfToken = createToken();
                    session.setAttribute(CSRF_SESSION_ATTRIBUTE, csrfToken);
                }
                request.setAttribute(CSRF_SESSION_ATTRIBUTE, csrfToken);
            } else if ("POST".equalsIgnoreCase(request.getMethod())) {
                Object storedToken = session.getAttribute(CSRF_SESSION_ATTRIBUTE);
                String expectedToken = storedToken instanceof String ? (String) storedToken : null;
                String submittedToken = request.getHeader("X-CSRF-Token");
                if (!matchesToken(expectedToken, submittedToken)) {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN,
                            "The submission security token is invalid or expired.");
                    return;
                }
                request.setAttribute(AUTHENTICATED_STUDENT_ATTRIBUTE, Boolean.TRUE);
            } else {
                response.setHeader("Allow", "GET, POST");
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

    private static void rejectUnauthenticated(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            response.sendRedirect(request.getContextPath() + "/login");
        } else {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Please sign in before continuing.");
        }
    }

    private static String createToken() {
        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private static boolean matchesToken(String expectedToken, String submittedToken) {
        return expectedToken != null && !expectedToken.isEmpty() && submittedToken != null
                && MessageDigest.isEqual(
                        expectedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        submittedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
