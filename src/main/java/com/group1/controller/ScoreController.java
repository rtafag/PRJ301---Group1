package com.group1.controller;

import com.group1.dao.ScoreDAO;
import com.group1.model.Score;
import com.group1.util.DBConnection;
import java.io.IOException;
import java.sql.Connection;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet("/scores")
public class ScoreController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try (Connection connection = DBConnection.getConnection()) {
            List<Score> scores = new ScoreDAO(connection).findAll();
            request.setAttribute("scores", scores);
            request.getRequestDispatcher("/WEB-INF/views/scores.jsp").forward(request, response);
        } catch (Exception exception) {
            throw new ServletException("Unable to load scores.", exception);
        }
    }
}