package com.group1.controller;

import com.group1.dao.RuleDefinitionDAO;
import com.group1.dao.ScoreDAO;
import com.group1.dao.SubmissionDAO;
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

@WebServlet(urlPatterns = {"", "/home", "/dashboard"})
public class HomeController extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Object currentUser = session == null ? null : session.getAttribute("currentUser");
        Object userId = session == null ? null : session.getAttribute("userId");
        if (!(currentUser instanceof Users) || !(userId instanceof String)
                || !((String) userId).trim().equals(((Users) currentUser).getUserId())) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        try (Connection conn = DBConnection.getConnection()) {
            int userCount = new UsersDAO(conn).count();
            int subCount = new SubmissionDAO(conn).count();
            int scoreCount = new ScoreDAO(conn).count();
            int ruleCount = new RuleDefinitionDAO(conn).count();

            request.setAttribute("userCount", userCount);
            request.setAttribute("subCount", subCount);
            request.setAttribute("scoreCount", scoreCount);
            request.setAttribute("ruleCount", ruleCount);

            request.getRequestDispatcher("/WEB-INF/views/home.jsp").forward(request, response);
        } catch (Exception e) {
            request.setAttribute("dbError", e.getMessage());
            request.getRequestDispatcher("/WEB-INF/views/home.jsp").forward(request, response);
        }
    }
}
