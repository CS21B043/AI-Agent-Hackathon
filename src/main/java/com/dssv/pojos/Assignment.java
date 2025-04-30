package com.dssv.pojos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects; // For potential null checks

public class Assignment {
    private String id;
    // Replaced single studentId with a list
    private List<String> studentIds;
    private String description;
    private String code; // Could be starter code, or expected structure
    private String testCases; // Could be text description or actual test code

    // Constructors
    // Constructor for creating a new assignment (ID might be generated later)
    public Assignment(String description, String code, String testCases) {
        this.description = description;
        this.code = code;
        this.testCases = testCases;
        this.studentIds = new ArrayList<>(); // Initialize list
    }

    // Full constructor (e.g., when reading from DB)
    public Assignment(String id, List<String> studentIds, String description, String code, String testCases) {
        this.id = id;
        this.studentIds = (studentIds != null) ? new ArrayList<>(studentIds) : new ArrayList<>(); // Defensive copy
        this.description = description;
        this.code = code;
        this.testCases = testCases;
    }

    // Default constructor needed by frameworks like Jackson for deserialization
    public Assignment() {
         this.studentIds = new ArrayList<>(); // Initialize list
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    // Getter for the list
    public List<String> getStudentIds() {
        // Return a copy to prevent external modification of the internal list (optional but good practice)
        // return new ArrayList<>(studentIds);
        // Or return direct reference if modifications are intended/managed elsewhere
         return studentIds;
    }

    // Setter for the list (takes a list as input)
    public void setStudentIds(List<String> studentIds) {
        this.studentIds = (studentIds != null) ? new ArrayList<>(studentIds) : new ArrayList<>(); // Defensive copy
    }

    // Convenience method to add a single student ID
    public void addStudentId(String studentId) {
        if (studentId != null && !studentId.isBlank() && !this.studentIds.contains(studentId)) {
            this.studentIds.add(studentId);
        }
    }

     // Convenience method to remove a single student ID
     public void removeStudentId(String studentId) {
         if (studentId != null) {
             this.studentIds.remove(studentId);
         }
     }


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
               ", studentIds=" + studentIds + // Updated field
               ", description='" + description + '\'' +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Assignment that = (Assignment) o;
        return Objects.equals(id, that.id); // Primarily check ID for equality
    }

    @Override
    public int hashCode() {
        return Objects.hash(id); // Use ID for hashcode
    }
}