package com.group1.model;
public class Submission {
    public String submissionId;
    public String userId;
    public String fileUrl;
    public String fileHash;
    public String status;
    public String submittedAt;

    public Submission(String submissionId, String userId, String fileUrl, String fileHash, String status, String submittedAt) {
        this.submissionId=submissionId; this.userId=userId; this.fileUrl=fileUrl;
        this.fileHash=fileHash; this.status=status; this.submittedAt=submittedAt;
    }

    public String getSubmissionId() { return submissionId; }
    public String getUserId() { return userId; }
    public String getFileUrl() { return fileUrl; }
    public String getFileName() {
        int separator = Math.max(fileUrl.lastIndexOf('/'), fileUrl.lastIndexOf('\\'));
        return fileUrl.substring(separator + 1);
    }
    public String getFileHash() { return fileHash; }
    public String getStatus() { return status; }
    public String getSubmittedAt() { return submittedAt; }
}
