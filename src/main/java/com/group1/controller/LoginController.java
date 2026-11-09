package com.group1.controller;

import com.group1.dao.UsersDAO;
import com.group1.model.Users;
import com.group1.util.DBConnection;
import com.group1.util.JwtUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/login")
public class LoginController extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("currentUser") != null) {
            response.sendRedirect(request.getContextPath() + "/home");
            return;
        }

        String errorParam = request.getParameter("error");
        if ("jwt_expired".equals(errorParam)) {
            request.setAttribute("errorMessage", "Phiên đăng nhập JWT đã hết hạn hoặc chưa được xác thực. Vui lòng đăng nhập lại.");
        }

        request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        String username = request.getParameter("username");
        String password = request.getParameter("password");

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            request.setAttribute("errorMessage", "Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
            request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
            return;
        }

        username = username.trim();
        password = password.trim();

        try (Connection conn = DBConnection.getConnection()) {
            UsersDAO usersDAO = new UsersDAO(conn);

            // Compute SHA-256 hash of provided password
            String sha256Hex = computeSha256(password);

            // Attempt login with real SHA-256 hash, then legacy fallbacks
            Users user = usersDAO.login(username, sha256Hex);
            if (user == null) {
                user = usersDAO.login(username, password);
            }
            if (user == null && !password.startsWith("hash_")) {
                user = usersDAO.login(username, "hash_" + password);
            }
            if (user == null) {
                user = usersDAO.findByUsername(username);
                if (user != null) {
                    String storedHash = user.getPasswordHash();
                    if (!sha256Hex.equalsIgnoreCase(storedHash) &&
                        !password.equals(storedHash) &&
                        !("hash_" + password).equals(storedHash)) {
                        user = null;
                    }
                }
            }

            if (user != null) {
                // 1. Generate standard RFC 7519 JWT (HS256)
                String jwtToken = JwtUtil.generateToken(user.getUserId(), user.getUsername(), user.getRole());

                // 2. Set Secure / HttpOnly JWT Cookie
                Cookie jwtCookie = new Cookie("jwt_token", jwtToken);
                jwtCookie.setHttpOnly(true);
                jwtCookie.setSecure(request.isSecure());
                jwtCookie.setPath("/");
                jwtCookie.setMaxAge(24 * 60 * 60); // 24 hours
                response.addCookie(jwtCookie);

                // 3. Set Authorization Header
                response.setHeader("Authorization", "Bearer " + jwtToken);

                // 4. Set Session attributes (both userId and jwtToken)
                HttpSession session = request.getSession(true);
                session.setAttribute("currentUser", user);
                session.setAttribute("userId", user.getUserId());
                session.setAttribute("jwtToken", jwtToken);

                response.sendRedirect(request.getContextPath() + "/home");
            } else {
                request.setAttribute("errorMessage", "Sai tên đăng nhập hoặc mật khẩu.");
                request.setAttribute("lastUsername", username);
                request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
            }
        } catch (Exception e) {
            request.setAttribute("errorMessage", "Lỗi xác thực hệ thống: " + e.getMessage());
            request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
        }
    }

    private static String computeSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
