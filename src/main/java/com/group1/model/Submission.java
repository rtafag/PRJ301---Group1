package com.group1.model;

import java.io.Serializable;

public class Submission implements Serializable {
    private static final long serialVersionUID = 1L;

    public String submissionId;
    public String userId;
    public String fileUrl;
    public String fileHash;
    public String status;
    public String submittedAt;

    public Submission() {
    }

    public Submission(String submissionId, String userId, String fileUrl, String fileHash, String status, String submittedAt) {
        this.submissionId = submissionId;
        this.userId = userId;
        this.fileUrl = fileUrl;
        this.fileHash = fileHash;
        this.status = status;
        this.submittedAt = submittedAt;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(String submittedAt) {
        this.submittedAt = submittedAt;
    }

    @Override
    public String toString() {
        return "Submission{" +
                "submissionId='" + submissionId + '\'' +
                ", userId='" + userId + '\'' +
                ", status='" + status + '\'' +
                ", submittedAt='" + submittedAt + '\'' +
                '}';
    }
}
