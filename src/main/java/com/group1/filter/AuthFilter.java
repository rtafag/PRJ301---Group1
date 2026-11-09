package com.group1.filter;

import com.group1.util.JwtUtil;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Security Filter validating standard RFC 7519 JWT (HS256) on protected routes.
 * Intercepts requests to /home, /submission, /score, and /api/* endpoints.
 */
@WebFilter(filterName = "AuthFilter", urlPatterns = {"/home", "/submission", "/score", "/api/*"})
public class AuthFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization if needed
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getRequestURI();
        // Skip login and static assets
        if (path.endsWith("/login") || path.endsWith("/logout") || path.contains("/static/") || path.contains("/css/")) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractJwtToken(request);

        if (token != null && JwtUtil.validateToken(token)) {
            JwtUtil.Claims claims = JwtUtil.getClaims(token);
            request.setAttribute("jwtClaims", claims);
            request.setAttribute("authenticatedUserId", claims.getUserId());
            request.setAttribute("authenticatedRole", claims.getRole());

            // Ensure session also stays synchronized
            HttpSession session = request.getSession(true);
            if (session.getAttribute("jwtToken") == null) {
                session.setAttribute("jwtToken", token);
            }

            chain.doFilter(request, response);
        } else {
            // Check fallback session for legacy requests
            HttpSession session = request.getSession(false);
            if (session != null && session.getAttribute("currentUser") != null) {
                chain.doFilter(request, response);
                return;
            }

            // Redirect to login if unauthenticated or JWT expired
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            if (path.startsWith(request.getContextPath() + "/api/")) {
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing JWT token\"}");
            } else {
                response.sendRedirect(request.getContextPath() + "/login?error=jwt_expired");
            }
        }
    }

    private String extractJwtToken(HttpServletRequest request) {
        // 1. Check Authorization Bearer header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.trim().startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        // 2. Check HTTP Cookies for jwt_token
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if ("jwt_token".equalsIgnoreCase(c.getName()) && c.getValue() != null && !c.getValue().trim().isEmpty()) {
                    return c.getValue().trim();
                }
            }
        }

        // 3. Check session attribute
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("jwtToken") != null) {
            return (String) session.getAttribute("jwtToken");
        }

        return null;
    }

    @Override
    public void destroy() {
        // Cleanup if needed
    }
}
