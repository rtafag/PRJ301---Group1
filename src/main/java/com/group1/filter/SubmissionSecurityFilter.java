package com.group1.filter;

import com.group1.model.Users;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebFilter(filterName = "SubmissionSecurityFilter", urlPatterns = {"/student/*", "/teacher/*"})
public class SubmissionSecurityFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(javax.servlet.ServletRequest request, javax.servlet.ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpSession session = httpRequest.getSession(false);

        Object sessionUserId = session == null ? null : session.getAttribute("userId");
        Object currentUser = session == null ? null : session.getAttribute("currentUser");
        if (!(sessionUserId instanceof String) || ((String) sessionUserId).trim().isEmpty()
                || !(currentUser instanceof Users)
                || !((String) sessionUserId).trim().equals(((Users) currentUser).getUserId())) {
            if ("GET".equalsIgnoreCase(httpRequest.getMethod())) {
                httpResponse.sendRedirect(httpRequest.getContextPath() + "/login");
            } else {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        "Please sign in before accessing the submission portal.");
            }
            return;
        }

        String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
        String requiredRole = path.startsWith("/teacher/") ? "teacher" : "student";
        if (!requiredRole.equalsIgnoreCase(((Users) currentUser).getRole())) {
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Your account role cannot access this portal.");
            return;
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
