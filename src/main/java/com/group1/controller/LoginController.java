package com.group1.controller;

import com.group1.dao.UsersDAO;
import com.group1.model.SessionUser;
import com.group1.model.Users;
import com.group1.util.DBConnection;
import com.group1.util.WebSecurity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet(urlPatterns = {"/login", "/logout"})
public class LoginController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if ("/logout".equals(request.getServletPath())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return;
        }
        request.setAttribute("csrfToken", WebSecurity.getOrCreateCsrfToken(request.getSession()));
        request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        if ("/logout".equals(request.getServletPath())) {
            logout(request, response);
            return;
        }

        HttpSession existingSession = request.getSession(false);
        if (existingSession == null
                || !WebSecurity.isValidCsrfToken(existingSession, request.getParameter("csrfToken"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid security token.");
            return;
        }

        String username = request.getParameter("username");
        String password = request.getParameter("password");
        if (username == null || password == null || username.trim().isEmpty()) {
            showLoginError(request, response, "Nhập tên đăng nhập và mật khẩu.");
            return;
        }

        try (Connection connection = DBConnection.getConnection()) {
            Users user = new UsersDAO(connection).findByUsername(username.trim());
            if (user == null || !passwordMatches(password, user.passwordHash, user.username)) {
                showLoginError(request, response, "Tên đăng nhập hoặc mật khẩu không đúng.");
                return;
            }
            if (!"Student".equalsIgnoreCase(user.role)) {
                showLoginError(request, response, "Cổng nộp bài hiện chỉ dành cho sinh viên.");
                return;
            }

            if (existingSession != null) existingSession.invalidate();
            HttpSession session = request.getSession(true);
            session.setAttribute(WebSecurity.SESSION_USER,
                    new SessionUser(user.userId, user.username, user.fullName, user.role));
            WebSecurity.getOrCreateCsrfToken(session);
            response.sendRedirect(request.getContextPath() + "/submissions");
        } catch (SQLException exception) {
            throw new ServletException("Unable to authenticate the user.", exception);
        }
    }

    private void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !WebSecurity.isValidCsrfToken(session, request.getParameter("csrfToken"))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid security token.");
            return;
        }
        session.invalidate();
        response.sendRedirect(request.getContextPath() + "/login");
    }

    private void showLoginError(HttpServletRequest request, HttpServletResponse response, String message)
            throws ServletException, IOException {
        request.setAttribute("errorMessage", message);
        request.setAttribute("csrfToken", WebSecurity.getOrCreateCsrfToken(request.getSession()));
        request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
    }

    private boolean passwordMatches(String candidate, String stored, String username) {
        if (candidate == null || stored == null) return false;
        if (stored.startsWith("sha256:")) {
            return MessageDigest.isEqual(
                    stored.substring("sha256:".length()).getBytes(StandardCharsets.US_ASCII),
                    WebSecurity.sha256Hex(candidate).getBytes(StandardCharsets.US_ASCII));
        }
        String demoPassword = "hash_" + username;
        return stored.equals(demoPassword)
                && MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8),
                        demoPassword.getBytes(StandardCharsets.UTF_8));
    }
}
