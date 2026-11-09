package com.group1.controller;

import com.group1.dao.AnalysisResultDAO;
import com.group1.dao.RuleDefinitionDAO;
import com.group1.dao.RuleViolationDAO;
import com.group1.dao.SubmissionDAO;
import com.group1.model.AnalysisResult;
import com.group1.model.DashboardFinding;
import com.group1.model.RuleDefinition;
import com.group1.model.RuleViolation;
import com.group1.model.Submission;
import com.group1.model.Users;
import com.group1.util.DBConnection;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@WebServlet(urlPatterns = {"/student/analysis", "/teacher/analysis"})
public class AnalysisDashboardController extends HttpServlet {
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
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Please sign in to view analysis results.");
            return;
        }
        boolean teacherView = request.getRequestURI()
                .substring(request.getContextPath().length()).startsWith("/teacher/");
        String requiredRole = teacherView ? "teacher" : "student";
        if (!requiredRole.equalsIgnoreCase(((Users) currentUser).getRole())) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Your account role cannot view this dashboard.");
            return;
        }

        String submissionId = request.getParameter("submissionId");
        if (submissionId == null || submissionId.trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "A submission ID is required.");
            return;
        }

        try (Connection connection = openConnection()) {
            Submission submission = new SubmissionDAO(connection).findById(submissionId.trim());
            if (submission == null
                    || (!teacherView && !((String) userId).trim().equals(submission.getUserId()))) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Submission not found.");
                return;
            }

            AnalysisResult analysis = new AnalysisResultDAO(connection)
                    .findBySubmissionId(submission.getSubmissionId());
            List<DashboardFinding> findings = new ArrayList<>();
            if (analysis != null) {
                Map<Integer, RuleDefinition> rulesById = new HashMap<>();
                for (RuleDefinition rule : new RuleDefinitionDAO(connection).findAll()) {
                    rulesById.put(rule.getRuleId(), rule);
                }
                for (RuleViolation violation : new RuleViolationDAO(connection)
                        .findByAnalysisId(analysis.getAnalysisId())) {
                    findings.add(new DashboardFinding(rulesById.get(violation.getRuleId()), violation));
                }
            }

            request.setAttribute("submission", submission);
            request.setAttribute("analysis", analysis);
            request.setAttribute("findings", findings);
            request.getRequestDispatcher("/WEB-INF/views/analysis-dashboard.jsp").forward(request, response);
        } catch (SQLException exception) {
            throw new ServletException("Unable to load the submission analysis.", exception);
        }
    }

    protected Connection openConnection() throws SQLException {
        return DBConnection.getConnection();
    }
}
