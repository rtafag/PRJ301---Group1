package com.group1.filter;

import com.group1.model.SessionUser;
import com.group1.util.WebSecurity;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebFilter(urlPatterns = "/submissions")
public class SubmissionSecurityFilter implements Filter {
    private static final long MAX_REQUEST_SIZE = 11L * 1024 * 1024;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        HttpSession session = request.getSession(false);
        Object sessionUser = session == null ? null : session.getAttribute(WebSecurity.SESSION_USER);

        if (!(sessionUser instanceof SessionUser)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }
        SessionUser user = (SessionUser) sessionUser;
        if (!"Student".equalsIgnoreCase(user.getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Only students may access submissions.");
            return;
        }

        String method = request.getMethod();
        if ("GET".equalsIgnoreCase(method)) {
            request.setAttribute("sessionUser", user);
            request.setAttribute("csrfToken", WebSecurity.getOrCreateCsrfToken(session));
            chain.doFilter(request, response);
            return;
        }
        if (!"POST".equalsIgnoreCase(method)) {
            response.setHeader("Allow", "GET, POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }

        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT).startsWith("multipart/form-data")) {
            response.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "A multipart file upload is required.");
            return;
        }
        long contentLength = request.getContentLengthLong();
        if (contentLength > MAX_REQUEST_SIZE) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Upload exceeds the 10 MB limit.");
            return;
        }
        String csrfToken;
        try {
            csrfToken = request.getParameter("csrfToken");
        } catch (IllegalStateException exception) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Upload exceeds the 10 MB limit.");
            return;
        }
        if (!WebSecurity.isValidCsrfToken(session, csrfToken)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid security token.");
            return;
        }

        request.setAttribute("sessionUser", user);
        request.setAttribute("csrfToken", WebSecurity.getOrCreateCsrfToken(session));
        chain.doFilter(request, response);
    }
}
