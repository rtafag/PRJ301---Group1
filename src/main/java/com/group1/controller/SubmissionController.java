package com.group1.controller;

import com.group1.dao.SubmissionDAO;
import com.group1.model.Submission;
import com.group1.util.DBConnection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

@WebServlet("/student/submissions")
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 10 * 1024 * 1024,
        maxRequestSize = 11 * 1024 * 1024
)
public class SubmissionController extends HttpServlet {
    private static final long MAX_ARCHIVE_CONTENT_SIZE = 50L * 1024 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 1000;
    private static final DateTimeFormatter SUBMITTED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        Object sessionUserId = session == null ? null : session.getAttribute("userId");
        if (!(sessionUserId instanceof String) || ((String) sessionUserId).trim().isEmpty()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Please sign in before submitting a file.");
            return;
        }

        Part uploadedFile;
        try {
            uploadedFile = request.getPart("submissionFile");
        } catch (IllegalStateException exception) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "The ZIP file exceeds the upload limit.");
            return;
        }
        if (uploadedFile == null || uploadedFile.getSize() == 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Choose a ZIP file to submit.");
            return;
        }

        String submittedName = Paths.get(uploadedFile.getSubmittedFileName() == null
                ? "" : uploadedFile.getSubmittedFileName()).getFileName().toString();
        if (!submittedName.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Only ZIP files are accepted.");
            return;
        }

        byte[] archiveBytes;
        try (InputStream input = uploadedFile.getInputStream()) {
            archiveBytes = readBounded(input, 10L * 1024 * 1024);
        } catch (IOException exception) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unable to read the uploaded ZIP file.");
            return;
        }
        if (!isValidZip(archiveBytes)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "The uploaded file is not a valid or safe ZIP archive.");
            return;
        }

        String fileHash = sha256(archiveBytes);
        String submissionId = "SUB-" + UUID.randomUUID().toString();
        Path savedFile = null;
        try (Connection connection = DBConnection.getConnection()) {
            SubmissionDAO submissionDAO = new SubmissionDAO(connection);
            if (submissionDAO.findByFileHash(fileHash) != null) {
                response.sendError(HttpServletResponse.SC_CONFLICT, "This ZIP file has already been submitted.");
                return;
            }

            Path uploadDirectory = Paths.get(System.getProperty("java.io.tmpdir"), "aita-submissions");
            Files.createDirectories(uploadDirectory);
            savedFile = uploadDirectory.resolve(submissionId + ".zip");
            Files.write(savedFile, archiveBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            Submission submission = new Submission(
                    submissionId,
                    ((String) sessionUserId).trim(),
                    savedFile.toAbsolutePath().toString(),
                    fileHash,
                    "Submitted",
                    LocalDateTime.now().format(SUBMITTED_AT_FORMAT));
            submissionDAO.insert(submission);

            response.setStatus(HttpServletResponse.SC_CREATED);
            response.getWriter().write("{\"submissionId\":\"" + submissionId
                    + "\",\"sha256\":\"" + fileHash + "\",\"status\":\"Submitted\"}");
        } catch (SQLException exception) {
            removeSavedFile(savedFile);
            throw new ServletException("Unable to save the submission record.", exception);
        } catch (IOException exception) {
            removeSavedFile(savedFile);
            throw exception;
        }
    }

    private static byte[] readBounded(InputStream input, long maximumSize) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            if (output.size() + (long) bytesRead > maximumSize) {
                throw new IOException("Upload exceeds the maximum size.");
            }
            output.write(buffer, 0, bytesRead);
        }
        return output.toByteArray();
    }

    private static boolean isValidZip(byte[] archiveBytes) {
        int entryCount = 0;
        long expandedSize = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archiveBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_ARCHIVE_ENTRIES || isUnsafeEntryName(entry.getName())) {
                    return false;
                }
                int bytesRead;
                while ((bytesRead = zip.read(buffer)) != -1) {
                    expandedSize += bytesRead;
                    if (expandedSize > MAX_ARCHIVE_CONTENT_SIZE) {
                        return false;
                    }
                }
                zip.closeEntry();
            }
            return entryCount > 0;
        } catch (IOException exception) {
            return false;
        }
    }

    private static boolean isUnsafeEntryName(String entryName) {
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            return true;
        }
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }

    private static String sha256(byte[] bytes) throws ServletException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new ServletException("SHA-256 is not available on this server.", exception);
        }
    }

    private static void removeSavedFile(Path file) throws ServletException {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            throw new ServletException("Unable to clean up an incomplete submission file.", exception);
        }
    }
}
