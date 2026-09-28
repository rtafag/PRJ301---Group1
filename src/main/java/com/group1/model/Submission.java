package com.group1.model;
public class Submission {
    public String submissionId; public String userId; public String fileUrl;
    public String fileHash; public String status; public String submittedAt;
    public Submission(String submissionId, String userId, String fileUrl, String fileHash, String status, String submittedAt) {
        this.submissionId=submissionId; this.userId=userId; this.fileUrl=fileUrl;
        this.fileHash=fileHash; this.status=status; this.submittedAt=submittedAt;
    }
}
