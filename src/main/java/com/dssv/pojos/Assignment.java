package com.dssv.pojos
class Assignment {
    private String id;
    private String studentId; // Important for solo assignments and updates
    private String description;
    private String code; // Could be starter code, or expected structure
    private String testCases; // Could be text description or actual test code

    // Constructors, Getters, Setters
    public Assignment(String id, String description, String code, String testCases) {
        this.id = id;
        this.description = description;
        this.code = code;
        this.testCases = testCases;
    }
     // Default constructor might be needed by frameworks/libraries
     public Assignment(){}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getStudentId() { return studentId; }
    public void setStudentId(String studentId) { this.studentId = studentId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTestCases() { return testCases; }
    public void setTestCases(String testCases) { this.testCases = testCases; }

    @Override
    public String toString() {
        return "Assignment{" +
               "id='" + id + '\'' +
               ", studentId='" + studentId + '\'' +
               ", description='" + description + '\'' +
               '}';
    }
}