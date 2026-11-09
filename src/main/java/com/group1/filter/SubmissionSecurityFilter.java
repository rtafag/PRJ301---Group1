package com.group1.filter;

import com.group1.model.Users;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

@WebFilter(filterName = "SubmissionSecurityFilter", urlPatterns = {"/student/*", "/teacher/*"})
public class SubmissionSecurityFilter implements Filter {
    public static final String SUBMISSION_SHA256_ATTRIBUTE = "submission.sha256";
    private static final long MAX_UPLOAD_SIZE = 10L * 1024 * 1024;

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(javax.servlet.ServletRequest request, javax.servlet.ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        HttpSession session = httpRequest.getSession(false);

        Object sessionUserId = session == null ? null : session.getAttribute("userId");
        Object currentUser = session == null ? null : session.getAttribute("currentUser");
        if (!(sessionUserId instanceof String) || ((String) sessionUserId).trim().isEmpty()
                || !(currentUser instanceof Users)
                || !((String) sessionUserId).trim().equals(((Users) currentUser).getUserId())) {
            if ("GET".equalsIgnoreCase(httpRequest.getMethod())) {
                httpResponse.sendRedirect(httpRequest.getContextPath() + "/login");
            } else {
                httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                        "Please sign in before accessing the submission portal.");
            }
            return;
        }

        String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
        String requiredRole = path.startsWith("/teacher/") ? "teacher" : "student";
        if (!requiredRole.equalsIgnoreCase(((Users) currentUser).getRole())) {
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Your account role cannot access this portal.");
            return;
        }

        if ("POST".equalsIgnoreCase(httpRequest.getMethod()) && "/student/submissions".equals(path)) {
            if (!validateAndHashUpload(httpRequest, httpResponse)) {
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private boolean validateAndHashUpload(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        Part uploadedFile;
        try {
            uploadedFile = request.getPart("submissionFile");
        } catch (IllegalStateException exception) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "The ZIP file exceeds the upload limit.");
            return false;
        }
        if (uploadedFile == null || uploadedFile.getSize() == 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Choose a ZIP file to submit.");
            return false;
        }
        if (uploadedFile.getSize() > MAX_UPLOAD_SIZE) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "The ZIP file exceeds the upload limit.");
            return false;
        }

        String submittedName = Paths.get(uploadedFile.getSubmittedFileName() == null
                ? "" : uploadedFile.getSubmittedFileName()).getFileName().toString();
        if (!submittedName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Only ZIP files are accepted.");
            return false;
        }

        try (InputStream input = uploadedFile.getInputStream()) {
            request.setAttribute(SUBMISSION_SHA256_ATTRIBUTE, sha256(input));
        } catch (NoSuchAlgorithmException exception) {
            throw new ServletException("SHA-256 is not available on this server.", exception);
        }
        return true;
    }

    private static String sha256(InputStream input) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int bytesRead;
        long totalBytes = 0;
        while ((bytesRead = input.read(buffer)) != -1) {
            totalBytes += bytesRead;
            if (totalBytes > MAX_UPLOAD_SIZE) {
                throw new IOException("Upload exceeds the configured maximum size.");
            }
            digest.update(buffer, 0, bytesRead);
        }

        StringBuilder hex = new StringBuilder(digest.getDigestLength() * 2);
        for (byte value : digest.digest()) {
            hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return hex.toString();
    }

    @Override
    public void destroy() {
    }
}
