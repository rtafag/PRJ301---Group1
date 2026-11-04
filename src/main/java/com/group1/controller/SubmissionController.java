package com.group1.controller;

import com.group1.dao.SubmissionDAO;
import com.group1.model.SessionUser;
import com.group1.model.Submission;
import com.group1.util.DBConnection;
import com.group1.util.WebSecurity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

@WebServlet("/submissions")
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 10L * 1024 * 1024,
        maxRequestSize = 11L * 1024 * 1024)
public class SubmissionController extends HttpServlet {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final DateTimeFormatter SUBMITTED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        SessionUser user = (SessionUser) request.getAttribute("sessionUser");
        try (Connection connection = DBConnection.getConnection()) {
            request.setAttribute("submissions", new SubmissionDAO(connection).findByUserId(user.getUserId()));
            request.getRequestDispatcher("/WEB-INF/views/submissions.jsp").forward(request, response);
        } catch (SQLException exception) {
            throw new ServletException("Unable to load submissions.", exception);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        SessionUser user = (SessionUser) request.getAttribute("sessionUser");
        Part filePart;
        try {
            filePart = request.getPart("sourceFile");
        } catch (IllegalStateException exception) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Upload exceeds the 10 MB limit.");
            return;
        }
        if (filePart == null || filePart.getSize() == 0) {
            showUploadError(request, response, "Chọn một tệp mã nguồn trước khi nộp.");
            return;
        }
        if (filePart.getSize() > MAX_FILE_SIZE) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Upload exceeds the 10 MB limit.");
            return;
        }

        String originalName = filePart.getSubmittedFileName();
        String safeName = originalName == null ? "" : originalName.replace('\\', '/');
        safeName = safeName.substring(safeName.lastIndexOf('/') + 1);
        String lowerName = safeName.toLowerCase(java.util.Locale.ROOT);
        if (!(lowerName.endsWith(".java") || lowerName.endsWith(".zip"))) {
            showUploadError(request, response, "Chỉ chấp nhận tệp .java hoặc .zip.");
            return;
        }

        String submissionId = UUID.randomUUID().toString();
        String storedName = submissionId + (lowerName.endsWith(".zip") ? ".zip" : ".java");
        Path storageDirectory = getStorageDirectory();
        Path destination = storageDirectory.resolve(storedName).normalize();
        if (!destination.getParent().equals(storageDirectory)) {
            throw new ServletException("Invalid upload destination.");
        }

        try {
            Files.createDirectories(destination.getParent());
            try (InputStream input = filePart.getInputStream()) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            String fileHash = sha256(destination);
            String submittedAt = LocalDateTime.now().format(SUBMITTED_AT);
            Submission submission = new Submission(
                    submissionId, user.getUserId(), destination.toString(), fileHash, "Pending", submittedAt);
            try (Connection connection = DBConnection.getConnection()) {
                new SubmissionDAO(connection).insert(submission);
            }
        } catch (IOException | SQLException | NoSuchAlgorithmException exception) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new ServletException("Unable to save the submission.", exception);
        }
        response.sendRedirect(request.getContextPath() + "/submissions?submitted=1");
    }

    private void showUploadError(HttpServletRequest request, HttpServletResponse response, String message)
            throws ServletException, IOException {
        request.setAttribute("errorMessage", message);
        doGet(request, response);
    }

    private Path getStorageDirectory() {
        String configuredDirectory = System.getenv("SUBMISSION_STORAGE_DIR");
        if (configuredDirectory != null && !configuredDirectory.trim().isEmpty()) {
            return Paths.get(configuredDirectory).toAbsolutePath().normalize();
        }
        String catalinaBase = System.getProperty("catalina.base");
        Path baseDirectory = catalinaBase == null
                ? Paths.get(System.getProperty("user.home"), ".aita")
                : Paths.get(catalinaBase);
        return baseDirectory.resolve("submission-storage").toAbsolutePath().normalize();
    }

    private String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) hex.append(String.format("%02x", value & 0xff));
        return hex.toString();
    }
}
