package com.dssv.database;
import com.dssv.pojos.*;
import java.util.List;
import java.io.IOException;

public interface DatabaseClient {
    List<Assignment> fetchAssignmentsByStudentId(String studentId) throws Exception;
    Assignment fetchAssignmentById(String assignmentId) throws Exception;
    void saveAssignment(Assignment assignment) throws Exception;
    List<Message> fetchConversationHistory(String studentId, String assignmentId) throws Exception;
    public void saveConversationHistory(String studentId, String assignmentId, List<Message> messages) throws Exception;
    // --- NEW Feedback Methods ---
    void saveFeedback(Feedback feedback) throws IOException;
    Feedback fetchFeedbackById(String feedbackId) throws IOException;
    List<Feedback> fetchFeedbacksByStudentId(String studentId) throws IOException;
    // Optional: List<Feedback> fetchFeedbacksByAssignmentId(String assignmentId) throws IOException;
}