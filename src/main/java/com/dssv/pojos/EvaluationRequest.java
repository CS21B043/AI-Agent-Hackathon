package com.dssv.pojos; 

// POJO for the /evaluate request body
public class EvaluationRequest {
    private String studentId;
    private String assignmentId;
    private String answer; // Student's code submission

    // No-args constructor for Jackson
    public EvaluationRequest() {}

    // Getters and Setters
    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}