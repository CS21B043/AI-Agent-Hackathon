package com.dssv.pojos;

/**
 * Request payload for initiating a discussion on an assignment.
 */
public class DiscussionRequest {
    private String studentId;
    private String assignmentId;
    private String message;

    // No-arg constructor
    public DiscussionRequest() { }

    // All-args constructor
    public DiscussionRequest(String studentId, String assignmentId, String message) {
        this.studentId     = studentId;
        this.assignmentId  = assignmentId;
        this.message       = message;
    }

    // Getters
    public String getStudentId() {
        return studentId;
    }
    public String getAssignmentId() {
        return assignmentId;
    }
    public String getMessage() {
        return message;
    }

    // Setters
    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }
    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }
    public void setMessage(String message) {
        this.message = message;
    }
}
