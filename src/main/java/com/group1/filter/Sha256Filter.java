package com.group1.filter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

@WebFilter(filterName = "sha256Filter")
public class Sha256Filter implements Filter {
    public static final String UPLOAD_BYTES_ATTRIBUTE = "submissionUploadBytes";
    public static final String UPLOAD_HASH_ATTRIBUTE = "submissionUploadSha256";
    public static final String UPLOAD_NAME_ATTRIBUTE = "submissionUploadName";
    private static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(javax.servlet.ServletRequest servletRequest,
            javax.servlet.ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        if (!Boolean.TRUE.equals(request.getAttribute(AuthFilter.AUTHENTICATED_STUDENT_ATTRIBUTE))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Authentication and CSRF validation must succeed before upload processing.");
            return;
        }

        Part uploadedFile;
        try {
            uploadedFile = request.getPart("submissionFile");
        } catch (IllegalStateException exception) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "The ZIP file exceeds the upload limit.");
            return;
        }
        if (uploadedFile == null || uploadedFile.getSize() == 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Choose a ZIP file to submit.");
            return;
        }
        if (uploadedFile.getSize() > MAX_UPLOAD_BYTES) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "The ZIP file exceeds the upload limit.");
            return;
        }

        byte[] contents;
        try (InputStream input = uploadedFile.getInputStream()) {
            contents = readBounded(input);
        } catch (UploadTooLargeException exception) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "The ZIP file exceeds the upload limit.");
            return;
        }
        request.setAttribute(UPLOAD_BYTES_ATTRIBUTE, contents);
        request.setAttribute(UPLOAD_HASH_ATTRIBUTE, sha256(contents));
        request.setAttribute(UPLOAD_NAME_ATTRIBUTE, uploadedFile.getSubmittedFileName());
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            if (output.size() + bytesRead > MAX_UPLOAD_BYTES) {
                throw new UploadTooLargeException();
            }
            output.write(buffer, 0, bytesRead);
        }
        return output.toByteArray();
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

    private static final class UploadTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
