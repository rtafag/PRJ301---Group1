package com.group1.controller;

import com.group1.dao.ScoreDAO;
import com.group1.dao.SubmissionDAO;
import com.group1.model.Users;
import com.group1.util.DBConnection;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet(urlPatterns = {"", "/home", "/dashboard"})
public class HomeController extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Object currentUser = session == null ? null : session.getAttribute("currentUser");
        Object sessionUserId = session == null ? null : session.getAttribute("userId");
        if (!(currentUser instanceof Users) || !(sessionUserId instanceof String)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        Users user = (Users) currentUser;
        String userId = ((String) sessionUserId).trim();
        if (userId.isEmpty() || user.getUserId() == null || !userId.equals(user.getUserId().trim())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "The signed-in user could not be verified.");
            return;
        }
        String role = user.getRole() == null ? "" : user.getRole().trim().toLowerCase(Locale.ROOT);
        if (!"student".equals(role) && !"teacher".equals(role) && !"admin".equals(role)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "This account cannot view the dashboard.");
            return;
        }

        response.setHeader("Cache-Control", "no-store");
        request.setAttribute("dashboardRole", role);
        if ("student".equals(role)) {
            request.getRequestDispatcher("/WEB-INF/views/home.jsp").forward(request, response);
            return;
        }

        try (Connection conn = openConnection()) {
            int subCount = new SubmissionDAO(conn).count();
            int scoreCount = new ScoreDAO(conn).count();

            request.setAttribute("subCount", subCount);
            request.setAttribute("scoreCount", scoreCount);

            request.getRequestDispatcher("/WEB-INF/views/home.jsp").forward(request, response);
        } catch (SQLException exception) {
            throw new ServletException("Unable to load dashboard statistics.", exception);
        }
    }

    protected Connection openConnection() throws SQLException {
        return DBConnection.getConnection();
    }
}
