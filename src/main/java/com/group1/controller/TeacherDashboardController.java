package com.group1.controller;

import com.group1.dao.SubmissionDAO;
import com.group1.model.Submission;
import com.group1.model.Users;
import com.group1.util.DBConnection;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet("/teacher/dashboard")
public class TeacherDashboardController extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute("userId");
        Object currentUser = session == null ? null : session.getAttribute("currentUser");
        if (!(userId instanceof String) || ((String) userId).trim().isEmpty()
                || !(currentUser instanceof Users)
                || !((String) userId).trim().equals(((Users) currentUser).getUserId())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Please sign in to view the teacher dashboard.");
            return;
        }
        if (!"teacher".equalsIgnoreCase(((Users) currentUser).getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Only teachers may access this dashboard.");
            return;
        }

        try (Connection connection = openConnection()) {
            List<Submission> submissions = new SubmissionDAO(connection).findAll();
            request.setAttribute("submissions", submissions);
            request.getRequestDispatcher("/WEB-INF/views/teacher-dashboard.jsp").forward(request, response);
        } catch (SQLException exception) {
            throw new ServletException("Unable to load the teacher dashboard.", exception);
        }
    }

    protected Connection openConnection() throws SQLException {
        return DBConnection.getConnection();
    }
}
