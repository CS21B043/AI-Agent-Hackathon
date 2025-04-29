package com.dssv.resources;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataParam;

import com.dssv.agents.*;
import com.dssv.database.*;
import com.dssv.pojos.*;

import java.util.List;
import java.util.Objects;

@Path("/teacher/v1")
public class TeacherAgentResource {

    private final TeacherAgent teacherAgent;

    @Inject
    public TeacherAgentResource() {
        DatabaseClient databaseClient = new FileDatabaseClient("database");
        NotifierAgent notifierAgent = new SseNotifierAgent();
        RetrieverAgent retrieverAgent = new BasicRetrieverAgent();
        // Load API key from environment
        String apiKey = System.getenv("GEMINI_API_KEY");
        Objects.requireNonNull(apiKey, "GEMINI_API_KEY must be set");

        // Construct the core agent
        this.teacherAgent = new TeacherAgent(apiKey, databaseClient, notifierAgent, retrieverAgent);
        System.out.println("TeacherAgentResource initialized with TeacherAgent");
    }

    // --- 1. Create Solo Assignment ---
    @POST
    @Path("/create/solo")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createSoloAssignment(
            @FormDataParam("studentId") String studentId,
            @FormDataParam("pdf") byte[] pdfBytes,
            @FormDataParam("topic") String topicOfInterest
    ) {
        if (studentId == null || studentId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\":\"studentId is required\"}").build();
        }
        try {
            Assignment assignment = teacherAgent.createSoloAssignment(
                studentId,
                pdfBytes,
                topicOfInterest
            );
            // Persist and notify
            teacherAgent.pushToDb(studentId, assignment);
            teacherAgent.notifyTeacher(assignment);
            teacherAgent.notifyStudent(studentId, assignment);

            return Response.ok(assignment).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\":\"" + e.getMessage() + "\"}")
                           .build();
        }
    }

    // --- 2. Create Group Assignment ---
    @POST
    @Path("/create/group")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createGroupAssignment(
            @FormDataParam("studentIds") List<String> studentIds,
            @FormDataParam("pdf") byte[] pdfBytes,
            @FormDataParam("topic") String topicOfInterest
    ) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\":\"at least one studentId is required\"}")
                           .build();
        }
        try {
            Assignment assignment = teacherAgent.createGroupAssignment(
                studentIds,
                pdfBytes,
                topicOfInterest
            );
            studentIds.forEach(id -> {
                try {
                    teacherAgent.pushToDb(id, assignment);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Failed to push assignment to DB for studentId: " + id);
                }
            });
            teacherAgent.notifyTeacher(assignment);
            studentIds.forEach(id -> {
                try {
                    teacherAgent.notifyStudent(id, assignment);
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Failed to notify student with studentId: " + id);
                }
            });

            return Response.ok(assignment).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\":\"" + e.getMessage() + "\"}")
                           .build();
        }
    }

    // --- 3. Update Assignment with Teacher Feedback ---
    @PUT
    @Path("/update/{assignmentId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateAssignment(
            @PathParam("assignmentId") String assignmentId,
            FeedbackRequest feedbackReq
    ) {
        if (assignmentId == null || assignmentId.isBlank()
                || feedbackReq == null
                || feedbackReq.getComments() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\":\"assignmentId and feedback are required\"}")
                           .build();
        }
        try {
            Feedback f = new Feedback(feedbackReq.getComments());
            Assignment updated = teacherAgent.updateAssignment(assignmentId, f);
            teacherAgent.pushToDb(updated.getStudentId(), updated);
            teacherAgent.notifyTeacher(updated);
            teacherAgent.notifyStudent(updated.getStudentId(), updated);

            return Response.ok(updated).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\":\"" + e.getMessage() + "\"}")
                           .build();
        }
    }

    // --- 4. Fetch All Assignments for a Student ---
    @GET
    @Path("/fetch/{studentId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response fetchAssignments(@PathParam("studentId") String studentId) {
        if (studentId == null || studentId.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\":\"studentId is required\"}").build();
        }
        try {
            List<Assignment> history = teacherAgent.fetchFromDb(studentId);
            return Response.ok(history).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\":\"" + e.getMessage() + "\"}")
                           .build();
        }
    }

    // --- 5. Discuss (Multi-Turn Chat) about an Assignment ---
    @POST
    @Path("/discuss")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response discussAssignment(DiscussionRequest req) {
        if (req == null
                || req.getStudentId() == null
                || req.getAssignmentId() == null
                || req.getMessage() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\":\"studentId, assignmentId, and message are required\"}")
                           .build();
        }
        try {
            List<Message> history = teacherAgent.fetchConversationHistory(
                req.getStudentId(),
                req.getAssignmentId()
            );
            String reply = teacherAgent.discuss(
                req.getStudentId(),
                req.getAssignmentId(),
                req.getMessage(),
                history
            );
            return Response.ok(reply).build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\":\"" + e.getMessage() + "\"}")
                           .build();
        }
    }
}
