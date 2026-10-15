package com.group1.controller;

import com.group1.dao.UsersDAO;
import com.group1.model.Users;
import com.group1.util.DBConnection;
import java.io.IOException;
import java.sql.Connection;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
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
            Users user = usersDAO.login(username, password);

            // Also check hash_username format if demo credentials used
            if (user == null && !password.startsWith("hash_")) {
                user = usersDAO.login(username, "hash_" + password);
            }
            if (user == null) {
                user = usersDAO.findByUsername(username);
                if (user != null && (!password.equals(user.getPasswordHash()) && !("hash_" + password).equals(user.getPasswordHash()))) {
                    user = null;
                }
            }

            if (user != null) {
                HttpSession session = request.getSession(true);
                session.setAttribute("currentUser", user);
                response.sendRedirect(request.getContextPath() + "/home");
            } else {
                request.setAttribute("errorMessage", "Sai tên đăng nhập hoặc mật khẩu.");
                request.setAttribute("lastUsername", username);
                request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
            }
        } catch (Exception e) {
            request.setAttribute("errorMessage", "Lỗi kết nối cơ sở dữ liệu: " + e.getMessage());
            request.getRequestDispatcher("/WEB-INF/views/login.jsp").forward(request, response);
        }
    }
}
