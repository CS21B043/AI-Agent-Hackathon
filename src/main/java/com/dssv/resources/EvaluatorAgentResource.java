package com.dssv.resources; 

import com.dssv.agents.EvaluatorAgent;
import com.dssv.database.DatabaseClient;
import com.dssv.database.FileDatabaseClient;
import com.dssv.pojos.*; // Import the request POJO
import com.dssv.gemini.GeminiApiClient; 

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/evaluator/v1")
public class EvaluatorAgentResource {

    private static final Logger LOGGER = Logger.getLogger(EvaluatorAgentResource.class.getName());
    private final EvaluatorAgent evaluatorAgent;

    @Inject
    public EvaluatorAgentResource() {
        LOGGER.info("Initializing EvaluatorAgentResource...");
        try {
            // --- Dependency Initialization (similar to TeacherAgentResource) ---

            // 1. Database Client
            DatabaseClient databaseClient = new FileDatabaseClient("database"); // Or get path from config

            // 2. Gemini API Key and Model
            String apiKey = System.getenv("GEMINI_API_KEY");
            Objects.requireNonNull(apiKey, "GEMINI_API_KEY environment variable must be set");

            // 4. Construct the Evaluator Agent Implementation
            this.evaluatorAgent = new EvaluatorAgent(apiKey, databaseClient);

            LOGGER.info("EvaluatorAgentResource initialized successfully.");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize DatabaseClient for EvaluatorAgentResource", e);
            throw new RuntimeException("FATAL: Failed to initialize DatabaseClient: " + e.getMessage(), e);
        } catch (NullPointerException e) {
             LOGGER.log(Level.SEVERE, "FATAL: Missing required environment variable (GEMINI_API_KEY or GEMINI_CODE_MODEL_ID)", e);
             throw new RuntimeException("FATAL: Missing required environment variable: " + e.getMessage(), e);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "FATAL: Unexpected error initializing EvaluatorAgentResource", e);
            throw new RuntimeException("FATAL: Unexpected error initializing evaluator: " + e.getMessage(), e);
        }
    }

    // --- API Endpoint for Evaluation ---
    @POST
    @Path("/evaluate")
    @Consumes(MediaType.APPLICATION_JSON) // Expect JSON input
    @Produces(MediaType.APPLICATION_JSON) // Produce JSON output (the raw result from Gemini)
    public Response evaluateAssignment(EvaluationRequest request) {
        LOGGER.log(Level.INFO, "Received evaluation request for student {0}, assignment {1}",
                   new Object[]{request.getStudentId(), request.getAssignmentId()});

        // --- Input Validation ---
        if (request == null ||
            request.getStudentId() == null || request.getStudentId().isBlank() ||
            request.getAssignmentId() == null || request.getAssignmentId().isBlank() ||
            request.getAnswer() == null /* Allow blank answer? Maybe not */ || request.getAnswer().isBlank())
        {
            LOGGER.warning("Evaluation request failed validation: Missing required fields.");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Missing required fields: studentId, assignmentId, and answer must be provided and non-blank.\"}")
                    .type(MediaType.APPLICATION_JSON) // Ensure error response type is JSON
                    .build();
        }

        try {
            // --- Call the Agent's evaluate method ---
            String evaluationResultJson = evaluatorAgent.evaluate(
                    request.getStudentId(),
                    request.getAssignmentId(),
                    request.getAnswer()
            );

            LOGGER.log(Level.INFO, "Evaluation completed for student {0}, assignment {1}",
                       new Object[]{request.getStudentId(), request.getAssignmentId()});

            // --- Return the result from the agent ---
            // The agent already returns a JSON string, so return it directly.
            return Response.ok(evaluationResultJson)
                           .type(MediaType.APPLICATION_JSON) // Ensure correct Content-Type header
                           .build();

        } catch (IllegalArgumentException e) {
             // Handle specific errors like "Assignment not found" or missing test cases from the agent
             LOGGER.log(Level.WARNING, "Evaluation failed for student {0}, assignment {1}: {2}",
                       new Object[]{request.getStudentId(), request.getAssignmentId(), e.getMessage()});
            return Response.status(Response.Status.BAD_REQUEST) // Or NOT_FOUND if appropriate
                    .entity("{\"error\":\"Evaluation failed: " + escapeJsonResponseString(e.getMessage()) + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        } catch (Exception e) {
            // Catch broader exceptions (API errors, DB save errors after evaluation, etc.)
            LOGGER.log(Level.SEVERE, "Internal error during evaluation for student {0}, assignment {1}",
                       new Object[]{request.getStudentId(), request.getAssignmentId()});
            e.printStackTrace(); // Log stack trace for internal errors
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"An internal error occurred during evaluation: " + escapeJsonResponseString(e.getMessage()) + "\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

     // Helper to escape strings for JSON output in error messages
     private String escapeJsonResponseString(String value) {
         if (value == null) return "";
         return value.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
     }
}