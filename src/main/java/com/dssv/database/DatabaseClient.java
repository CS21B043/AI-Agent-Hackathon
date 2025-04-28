package com.dssv.database;
import com.dssv.pojos.Assignment;
import java.util.List;
interface DatabaseClient {
    List<Assignment> fetchAssignmentsByStudentId(String studentId) throws Exception;
    Assignment fetchAssignmentById(String assignmentId) throws Exception;
    void saveAssignment(String studentId, Assignment assignment) throws Exception;
}