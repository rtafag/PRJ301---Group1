package com.group1.controller;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;

@WebFilter(filterName = "Sha256Filter", urlPatterns = "/student/submissions")
public class Sha256Filter implements Filter {
    public static final String SHA256_ATTRIBUTE = Sha256Filter.class.getName() + ".sha256";
    private static final String PART_NAME = "submissionFile";

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (!"POST".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = httpRequest.getSession(false);
        Object userId = session == null ? null : session.getAttribute("userId");
        if (!(userId instanceof String) || ((String) userId).trim().isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        Part uploadedFile;
        try {
            uploadedFile = httpRequest.getPart(PART_NAME);
        } catch (IllegalStateException exception) {
            chain.doFilter(request, response);
            return;
        }
        if (uploadedFile == null) {
            chain.doFilter(request, response);
            return;
        }

        byte[] content = readAll(uploadedFile.getInputStream());
        String hash = sha256(content);
        chain.doFilter(new CachedPartRequest(httpRequest, uploadedFile, content, hash), response);
    }

    @Override
    public void destroy() {
    }

    private static byte[] readAll(InputStream input) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = source.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            return output.toByteArray();
        }
    }

    private static String sha256(byte[] content) throws ServletException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new ServletException("SHA-256 is not available on this server.", exception);
        }
    }

    private static final class CachedPartRequest extends HttpServletRequestWrapper {
        private final Part originalPart;
        private final Part cachedPart;

        private CachedPartRequest(HttpServletRequest request, Part originalPart, byte[] content, String hash) {
            super(request);
            this.originalPart = originalPart;
            this.cachedPart = new CachedPart(originalPart, content);
            setAttribute(SHA256_ATTRIBUTE, hash);
        }

        @Override
        public Part getPart(String name) throws IOException, ServletException {
            return PART_NAME.equals(name) ? cachedPart : super.getPart(name);
        }

        @Override
        public Collection<Part> getParts() throws IOException, ServletException {
            List<Part> parts = new ArrayList<>();
            for (Part part : super.getParts()) {
                parts.add(part == originalPart ? cachedPart : part);
            }
            return parts;
        }
    }

    private static final class CachedPart implements Part {
        private final Part delegate;
        private final byte[] content;

        private CachedPart(Part delegate, byte[] content) {
            this.delegate = delegate;
            this.content = content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public String getContentType() {
            return delegate.getContentType();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public String getSubmittedFileName() {
            return delegate.getSubmittedFileName();
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public void write(String fileName) throws IOException {
            delegate.write(fileName);
        }

        @Override
        public void delete() throws IOException {
            delegate.delete();
        }

        @Override
        public String getHeader(String name) {
            return delegate.getHeader(name);
        }

        @Override
        public Collection<String> getHeaders(String name) {
            return delegate.getHeaders(name);
        }

        @Override
        public Collection<String> getHeaderNames() {
            return delegate.getHeaderNames();
        }
    }
}
