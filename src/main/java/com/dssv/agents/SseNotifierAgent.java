package com.dssv.agents;

import com.dssv.pojos.Assignment;
import com.dssv.services.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;

/**
 * Sends JSON‐named events via NotificationService’s SSE broadcasters.
 */
public class SseNotifierAgent implements NotifierAgent {

    @Inject
    private NotificationService notificationService;

    // Reuse a single mapper instance
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void notifyTeacher(Assignment assignment) {
        if (assignment == null) {
            System.err.println("SSE NOTIFIER ERROR: assignment is null for teacher notification.");
            return;
        }

        // TODO: Replace with real lookup of teacher ID(s)
        String teacherUserId = "teacher_admin";

        Map<String, String> payload = new HashMap<>();
        payload.put("type", "ASSIGNMENT_REVIEW");
        payload.put("assignmentId", assignment.getId() != null ? assignment.getId() : "N/A");
        String desc = assignment.getDescription();
        payload.put("description", 
            desc != null 
                ? desc.substring(0, Math.min(desc.length(), 100)) + "…" 
                : "N/A"
        );
        if (assignment.getStudentIds() != null) {
            for (String studentId : assignment.getStudentIds()) {
                payload.put("studentId", studentId);
            }
        }

        try {
            String json = objectMapper.writeValueAsString(payload);
            notificationService.sendNotification(
                teacherUserId, 
                json, 
                "teacher_notification"
            );
        } catch (Exception e) {
            System.err.println("SSE NOTIFIER ERROR: failed to send teacher notification: " 
                + e.getMessage());
        }
    }

    @Override
    public void notifyStudent(String studentId, Assignment assignment) {
        if (studentId == null || studentId.isBlank()) {
            System.err.println("SSE NOTIFIER ERROR: studentId is null/blank.");
            return;
        }
        if (assignment == null) {
            System.err.println("SSE NOTIFIER ERROR: assignment is null for student " 
                + studentId);
            return;
        }

        Map<String, String> payload = new HashMap<>();
        payload.put("type", "ASSIGNMENT_UPDATE");
        payload.put("assignmentId", assignment.getId() != null ? assignment.getId() : "N/A");
        String desc = assignment.getDescription();
        payload.put("description", 
            desc != null 
                ? desc.substring(0, Math.min(desc.length(), 100)) + "…" 
                : "N/A"
        );

        try {
            String json = objectMapper.writeValueAsString(payload);
            notificationService.sendNotification(
                studentId,
                json,
                "student_notification"
            );
        } catch (Exception e) {
            System.err.println("SSE NOTIFIER ERROR: failed to send student notification for " 
                + studentId + ": " + e.getMessage());
        }
    }
}
