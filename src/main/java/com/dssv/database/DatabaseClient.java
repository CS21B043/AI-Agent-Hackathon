package com.dssv.database;
import com.dssv.pojos.*;
import java.util.List;

public interface DatabaseClient {
    List<Assignment> fetchAssignmentsByStudentId(String studentId) throws Exception;
    Assignment fetchAssignmentById(String assignmentId) throws Exception;
    void saveAssignment(String studentId, Assignment assignment) throws Exception;
    List<Message> fetchConversationHistory(String studentId, String assignmentId) throws Exception;
}