package com.dssv.pojos;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;

import java.time.LocalDateTime;
import java.util.Objects;

public class Feedback {

    private String id; // Unique ID for this feedback entry
    private String studentId;
    private String assignmentId;
    private String comments; // Textual feedback from evaluator
    private Double accuracy; // Accuracy percentage (e.g., 80.5) - nullable
    private String error; // Error message during execution - nullable

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime timestamp; // When the feedback was generated

    // Default constructor (needed for Jackson/deserialization)
    public Feedback() {
    }

    public Feedback(String comments) {
        this.comments = comments;
    }

    // Constructor with all fields
    public Feedback(String id, String studentId, String assignmentId, String comments, Double accuracy, String error, LocalDateTime timestamp) {
        this.id = id;
        this.studentId = studentId;
        this.assignmentId = assignmentId;
        this.comments = comments;
        this.accuracy = accuracy;
        this.error = error;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

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

    public String getcomments() {
        return comments;
    }

    public void setcomments(String comments) {
        this.comments = comments;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(Double accuracy) {
        this.accuracy = accuracy;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    // --- Optional: equals, hashCode, toString ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Feedback feedback = (Feedback) o;
        return Objects.equals(id, feedback.id) && Objects.equals(studentId, feedback.studentId) && Objects.equals(assignmentId, feedback.assignmentId) && Objects.equals(comments, feedback.comments) && Objects.equals(accuracy, feedback.accuracy) && Objects.equals(error, feedback.error) && Objects.equals(timestamp, feedback.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, studentId, assignmentId, comments, accuracy, error, timestamp);
    }

    @Override
    public String toString() {
        return "Feedback{" +
                "id='" + id + '\'' +
                ", studentId='" + studentId + '\'' +
                ", assignmentId='" + assignmentId + '\'' +
                ", comments='" + comments + '\'' +
                ", accuracy=" + accuracy +
                ", error='" + error + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}