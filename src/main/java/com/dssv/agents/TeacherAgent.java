package com.dssv.agents;

import com.dssv.gemini.GeminiApiClient;
// Assuming GeminiModelInfo is not directly used here, removed import
// import com.dssv.gemini.GeminiModelInfo;

import com.dssv.database.DatabaseClient;

import com.dssv.agents.NotifierAgent;
import com.dssv.agents.RetrieverAgent;

// Assuming POJOs like Assignment, Feedback, Message are in this package
import com.dssv.pojos.*;

import com.dssv.logic.AssignmentGenerator;
import com.dssv.logic.AssignmentUpdater;

import java.util.ArrayList; // For creating new history list
import java.util.List;
import java.util.Objects;
import java.util.UUID; // For potentially generating conversation IDs if needed
import java.util.Collections;

import java.io.IOException; // For potential exceptions

// Logging framework (optional but recommended)
import java.util.logging.Level;
import java.util.logging.Logger;


public class TeacherAgent {

    private static final Logger LOGGER = Logger.getLogger(TeacherAgent.class.getName());

    private final GeminiApiClient geminiClient;
    private final DatabaseClient databaseClient;
    private final NotifierAgent notifierAgent;
    private final RetrieverAgent retrieverAgent;
    private final String defaultGeminiModelId = "gemini-1.5-flash"; // Or another suitable model

    public TeacherAgent(String apiKey, DatabaseClient databaseClient, NotifierAgent notifierAgent, RetrieverAgent retrieverAgent) {
        Objects.requireNonNull(apiKey, "API Key cannot be null");
        Objects.requireNonNull(databaseClient, "DatabaseClient cannot be null");
        Objects.requireNonNull(notifierAgent, "NotifierAgent cannot be null");
        Objects.requireNonNull(retrieverAgent, "RetrieverAgent cannot be null");

        this.geminiClient = new GeminiApiClient(apiKey);
        this.databaseClient = databaseClient;
        this.notifierAgent = notifierAgent;
        this.retrieverAgent = retrieverAgent;
    }

    /**
     * /Create_Solo: Creates a new personalized assignment for a student.
     * Automatically notifies the teacher after saving.
     */
    public Assignment createSoloAssignment(String studentId, byte[] pdfBytes, String topicOfInterest) throws Exception {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("Student ID cannot be null or empty.");
        }
        if (pdfBytes == null && (topicOfInterest == null || topicOfInterest.isBlank())) {
            throw new IllegalArgumentException("Either PDF content or a Topic of Interest must be provided.");
        }

        LOGGER.log(Level.INFO, "TeacherAgent: Creating solo assignment for student: {0}", studentId);

        // 1. Fetch previous FEEDBACK << CHANGE >>
        List<Feedback> previousFeedbacks = databaseClient.fetchFeedbacksByStudentId(studentId);
        LOGGER.log(Level.INFO, "TeacherAgent: Fetched {0} previous feedbacks.", (previousFeedbacks == null ? 0 : previousFeedbacks.size()));


        // 2. Analyze PDF
        String pdfAnalysisText = analyzePdf(pdfBytes, "Analyze the key concepts and potential programming assignment ideas from the provided document content. Focus on topics suitable for a personalized coding assignment.");

        // 3. Retrieve context
        String retrievedContext = retrieveContext(topicOfInterest);

        // 4. Generate Personalized Assignment
        LOGGER.info("TeacherAgent: Generating personalized assignment...");
        Assignment newAssignment = AssignmentGenerator.generatePersonalizedAssignment(
                studentId,
                previousFeedbacks,
                pdfAnalysisText,
                retrievedContext,
                topicOfInterest,
                geminiClient,
                defaultGeminiModelId
        );
        LOGGER.info("TeacherAgent: Assignment generated.");
        // 5. Push to DB
        LOGGER.info("TeacherAgent: Saving assignment to DB...");
        // Ensure assignment gets an ID before saving if not already set by generator
        if (newAssignment.getId() == null || newAssignment.getId().isBlank()) {
            newAssignment.setId("asgn-" + UUID.randomUUID().toString()); // Example ID generation
            LOGGER.log(Level.INFO, "Generated ID for new assignment: {0}", newAssignment.getId());
        }
        newAssignment.setSources(retrievedContext); // Set sources if needed
        pushToDb(newAssignment);  // << CHANGE >>: Pass only the assignment object
        LOGGER.info("TeacherAgent: Assignment saved.");

        // 6. Notify Teacher (HITL) - Happens *after* successful save
        LOGGER.info("TeacherAgent: Notifying teacher...");
        notifyTeacher(newAssignment); // Pass the saved assignment (potentially with DB-generated ID)
        LOGGER.info("TeacherAgent: Teacher notified.");

        // 7. Return the created assignment
        return newAssignment;
    }

    /**
     * /Create_Group: Creates a new assignment for a group of students.
     * Automatically notifies the teacher after saving for all students.
     */
    public Assignment createGroupAssignment(List<String> studentIds, byte[] pdfBytes, String topicOfInterest) throws Exception {
        if (studentIds == null || studentIds.isEmpty()) {
            throw new IllegalArgumentException("Student IDs list cannot be null or empty.");
        }
        if (pdfBytes == null && (topicOfInterest == null || topicOfInterest.isBlank())) {
            throw new IllegalArgumentException("Either PDF content or a Topic of Interest must be provided.");
        }

        LOGGER.log(Level.INFO, "TeacherAgent: Creating group assignment for {0} students.", studentIds.size());

        // 1. Analyze PDF
        String pdfAnalysisText = analyzePdf(pdfBytes, "Analyze the key concepts and potential programming assignment ideas from the provided document content. Focus on topics suitable for a general group coding assignment.");

        // 2. Retrieve context
        String retrievedContext = retrieveContext(topicOfInterest);

        // 3. Generate Group Assignment
        LOGGER.info("TeacherAgent: Generating group assignment...");
        Assignment newAssignment = AssignmentGenerator.generateGroupAssignment(
                studentIds,
                pdfAnalysisText,
                retrievedContext,
                topicOfInterest,
                geminiClient,
                defaultGeminiModelId
        );
        LOGGER.info("TeacherAgent: Group assignment generated.");

        // Ensure assignment has an ID *before* saving loop
        if (newAssignment.getId() == null || newAssignment.getId().isBlank()) {
             newAssignment.setId("asgn-" + UUID.randomUUID().toString()); // Example ID generation
             LOGGER.log(Level.INFO, "Generated ID for new group assignment: {0}", newAssignment.getId());
        }
        newAssignment.setSources(retrievedContext); // Set sources if needed
        // 4. Push the single assignment object to the database
        LOGGER.info("TeacherAgent: Saving group assignment to DB...");
        pushToDb(newAssignment); // << CHANGE >>: Pass only the assignment object
        LOGGER.info("TeacherAgent: Group assignment saved.");

        // 5. Notify the Teacher (HITL) - Notify once *after* attempting saves
        LOGGER.info("TeacherAgent: Notifying teacher about group assignment...");
        notifyTeacher(newAssignment); // Notify about the assignment content
        LOGGER.info("TeacherAgent: Teacher notified about group assignment.");
        // 6. Return the created assignment
        return newAssignment;
    }

    /**
      * /Update: Updates an existing assignment (could be solo or group).
      * Fetches by ID, updates content, saves the single object back.
      */
    public Assignment updateAssignment(String assignmentId, Feedback feedback) throws Exception {
        if (assignmentId == null || assignmentId.isBlank() || feedback == null) {
             throw new IllegalArgumentException("Assignment ID and Feedback cannot be null or empty.");
        }

        LOGGER.log(Level.INFO, "TeacherAgent: Updating assignment ID: {0}", assignmentId);

        // 1. Fetch the existing assignment by ID
        LOGGER.info("TeacherAgent: Fetching existing assignment from DB...");
        // << CHANGE >> Use internal fetch that doesn't rely on studentId parameter
        Assignment existingAssignment = databaseClient.fetchAssignmentById(assignmentId);
        if (existingAssignment == null) {
            throw new Exception("Assignment with ID " + assignmentId + " not found.");
        }
        LOGGER.info("TeacherAgent: Existing assignment fetched.");
        // Existing assignment now contains the list of associated student IDs

        // 2. Update the assignment content using AssignmentUpdater
        LOGGER.info("TeacherAgent: Applying updates based on feedback...");
        // Assuming updater modifies the content (description, code, testCases) based on feedback
        Assignment updatedAssignment = AssignmentUpdater.updateAssignment(
                existingAssignment, // Pass the original for context
                feedback,
                geminiClient,
                defaultGeminiModelId
        );

        // << CHANGE >>: Ensure ID and student list are preserved from the existing assignment
        updatedAssignment.setId(existingAssignment.getId());
        updatedAssignment.setStudentIds(existingAssignment.getStudentIds()); // Keep original students unless feedback explicitly changes them (advanced)

        LOGGER.info("TeacherAgent: Assignment content updated based on feedback.");

        // 3. Push the updated single assignment object back to the database
        LOGGER.info("TeacherAgent: Saving updated assignment...");
        pushToDb(updatedAssignment); // << CHANGE >>: Pass only the assignment object
        LOGGER.info("TeacherAgent: Updated assignment saved.");

        // 4. Notify the Teacher about the update completion
        LOGGER.info("TeacherAgent: Notifying teacher about the update completion...");
        notifyTeacher(updatedAssignment);
        LOGGER.info("TeacherAgent: Teacher notified about update.");

        // 5. Return (Student notification handled by REST layer)
        return updatedAssignment;
    }

    /**
     * Discuss: Handles multi-turn chat with a student about an assignment.
     * Fetches history, generates response, saves updated history.
     */
    public String discuss(String studentId, String assignmentId, String studentMessage) throws Exception {
        if (studentId == null || studentId.isBlank() || assignmentId == null || assignmentId.isBlank() || studentMessage == null || studentMessage.isBlank()) {
            throw new IllegalArgumentException("Student ID, Assignment ID, and Student Message are required for discussion.");
        }

        LOGGER.log(Level.INFO, "TeacherAgent: Handling discussion from student {0} about assignment {1}", new Object[]{studentId, assignmentId});

        // 1. Fetch existing conversation history
        List<Message> history;
        try {
            history = fetchConversationHistory(studentId, assignmentId);
            LOGGER.log(Level.INFO, "Fetched {0} messages from history.", history.size());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to fetch conversation history for student " + studentId + ", assignment " + assignmentId + ". Starting new history.", e);
            history = new ArrayList<>(); // Start fresh if fetch fails
        }

        // Wrap whatever you got into an ArrayList so you can add to it:
        history = new ArrayList<>(history);

        // 2. Fetch assignment details for context (optional but good)
        String assignmentContext = getAssignmentContextForChat(assignmentId);


        // 3. Add student's current message to history (in memory)
        // ASSUMPTION: Message class constructor Message(role, text) exists
        history.add(new Message("user", studentMessage));

        // 4. Construct the prompt/payload for Gemini's multi-turn chat
        String chatPayload = buildChatPayload(history, assignmentContext);
        // LOGGER.log(Level.FINE, "TeacherAgent: Sending chat payload to Gemini:\n{0}", chatPayload); // Log full payload only if needed (FINE level)
        LOGGER.log(Level.INFO, "TeacherAgent: Sending request to Gemini for chat response...");


        // 5. Call Gemini's multi-turn chat endpoint
        String responseText;
        try {
            responseText = geminiClient.generateMultiTurnChat(defaultGeminiModelId, chatPayload);
            LOGGER.info("TeacherAgent: Received chat response from Gemini.");
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "TeacherAgent: Error during chat generation with Gemini: " + e.getMessage(), e);
            // Remove the user message we added optimistically if the API call failed?
            // history.remove(history.size() - 1); // Optional rollback
            throw new Exception("Failed to get response for discussion.", e);
        }

        // 6. Add agent's response to history (in memory)
        history.add(new Message("model", responseText));

        // 7. Save the updated conversation history
        try {
            saveConversationHistory(studentId, assignmentId, history);
            LOGGER.log(Level.INFO, "Saved updated conversation history ({0} messages).", history.size());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "TeacherAgent: Failed to save conversation history for student " + studentId + ", assignment " + assignmentId, e);
            // Decide if this failure should prevent returning the response.
            // For now, log the error but still return the response obtained.
            // throw new Exception("Failed to save conversation history after discussion.", e); // Alternatively, throw
        }

        // 8. Return the agent's response text
        return responseText;
    }

    // --- Helper Methods ---

    /** Analyzes PDF bytes using Gemini. */
    private String analyzePdf(byte[] pdfBytes, String prompt) throws Exception {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return null;
        }
        LOGGER.info("TeacherAgent: Analyzing provided PDF...");
        try {
            String analysis = geminiClient.generateContentWithDocumentBytes(
                    defaultGeminiModelId, // Use a model capable of handling documents
                    prompt,
                    "application/pdf",
                    pdfBytes
            );
            LOGGER.info("TeacherAgent: PDF analysis result obtained.");
            return analysis;
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "TeacherAgent: Error analyzing PDF with Gemini: " + e.getMessage(), e);
            throw new Exception("Failed to analyze PDF content.", e);
        }
    }

    /** Retrieves context using RetrieverAgent. */
    private String retrieveContext(String topicOfInterest) {
        if (topicOfInterest == null || topicOfInterest.isBlank()) {
            return null;
        }
        LOGGER.log(Level.INFO, "TeacherAgent: Retrieving context for ToI: {0}", topicOfInterest);
        try {
            String context = retrieverAgent.retrieve(topicOfInterest);
            LOGGER.info("TeacherAgent: Retrieved context.");
            return context;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "TeacherAgent: Error retrieving context: " + e.getMessage() + ". Proceeding without retrieved context.", e);
            return null; // Proceed without context if retrieval fails
        }
    }

    /** Fetches assignment details to provide context for the chat. */
     private String getAssignmentContextForChat(String assignmentId) {
         try {
             Assignment assignment = databaseClient.fetchAssignmentById(assignmentId);
             if (assignment != null) {
                 // Simple context - adjust as needed
                 return String.format(" The discussion is about the assignment titled '%s'.",
                         assignment.getDescription() != null ? assignment.getDescription() : "N/A"
                         // Maybe add code snippet if small? Be careful with large context windows.
                         // + "\nCode Snippet: " + assignment.getCode()
                 );
             } else {
                 LOGGER.warning("TeacherAgent: Could not fetch details for assignment " + assignmentId + " during discussion.");
                 return " The discussion is about assignment ID " + assignmentId + ", but its details couldn't be fetched.";
             }
         } catch (Exception e) {
             LOGGER.log(Level.WARNING, "Error fetching assignment details for chat context (ID: " + assignmentId + "): " + e.getMessage(), e);
             return " Error fetching assignment details for context.";
         }
     }

    private String buildChatPayload(List<Message> history, String assignmentContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");

        // 1) Top-level system_instruction
        sb.append("\"system_instruction\":{")
        .append("\"parts\":[{")
        .append("\"text\":\"")
        .append(escapeJsonString(
            "You are a helpful teaching assistant. Answer the student's questions about their programming assignment clearly and concisely. Be encouraging."
            + assignmentContext))
        .append("\"}")
        .append("]},");  // close system_instruction

        // 2) Chat contents
        sb.append("\"contents\":[");
        for (int i = 0; i < history.size(); i++) {
            Message msg = history.get(i);
            String role = msg.getRole().equalsIgnoreCase("model") ? "assistant" 
                            : msg.getRole().toLowerCase();
            if (!role.equals("user") && !role.equals("assistant")) {
                LOGGER.warning("Skipping invalid role: " + role);
                continue;
            }
            sb.append("{")
            .append("\"role\":\"").append(role).append("\",")
            .append("\"parts\":[{\"text\":\"")
            .append(escapeJsonString(msg.getText()))
            .append("\"}]}");
            if (i < history.size() - 1) sb.append(",");
        }
        sb.append("],");  // close contents

        // 3) Generation config
        sb.append("\"generationConfig\":{")
        .append("\"temperature\":0.7,")
        .append("\"topP\":0.9")
        .append("}");

        sb.append("}");
        return sb.toString();
    }



    /** Helper for escaping JSON strings. */
    private String escapeJsonString(String input) {
        if (input == null) return "";
        // Basic escaping, consider using a library (like Jackson's StringEscapeUtils) for robustness
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
                    // Consider handling other control characters if necessary
    }

    // --- Database Interaction Methods ---

    public List<Assignment> fetchFromDb(String studentId) throws Exception {
        LOGGER.log(Level.INFO, "Fetching assignments for student {0} from DB.", studentId);
        return databaseClient.fetchAssignmentsByStudentId(studentId);
    }

    /** << CHANGE >>: Pushes a single Assignment object (containing student IDs) to DB */
    public void pushToDb(Assignment assignment) throws Exception {
        Objects.requireNonNull(assignment, "Assignment cannot be null for pushToDb");
        Objects.requireNonNull(assignment.getId(), "Assignment ID cannot be null for pushToDb");
        LOGGER.log(Level.INFO, "Saving assignment {0} (Students: {1}) to DB.",
                   new Object[]{assignment.getId(), assignment.getStudentIds()});
        // Database client now handles saving the object and updating the index for all students
        databaseClient.saveAssignment(assignment);
    }

     public List<Message> fetchConversationHistory(String studentId, String assignmentId) throws Exception {
        LOGGER.log(Level.INFO, "Fetching conversation history for student {0}, assignment {1}.", new Object[]{studentId, assignmentId});
         return databaseClient.fetchConversationHistory(studentId, assignmentId);
     }

     public void saveConversationHistory(String studentId, String assignmentId, List<Message> messages) throws Exception {
         LOGGER.log(Level.INFO, "Saving conversation history ({0} messages) for student {1}, assignment {2}.", new Object[]{messages.size(), studentId, assignmentId});
         databaseClient.saveConversationHistory(studentId, assignmentId, messages);
     }

    // --- Notification Methods ---

    public void notifyTeacher(Assignment assignment) throws Exception {
        LOGGER.log(Level.INFO, "Notifying teacher about assignment ID: {0}", assignment.getId());
        try {
            notifierAgent.notifyTeacher(assignment);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to notify teacher for assignment ID: " + assignment.getId(), e);
            throw e; // Re-throw to indicate notification failure
        }
    }

    public void notifyStudent(String studentId, Assignment assignment) throws Exception {
        if (studentId == null || studentId.isBlank()) {
             LOGGER.severe("TeacherAgent: Cannot notify student - invalid student ID provided.");
             throw new IllegalArgumentException("Invalid student ID for notification.");
        }
        if (assignment == null) {
             LOGGER.severe("TeacherAgent: Cannot notify student " + studentId + " - assignment is null.");
              throw new IllegalArgumentException("Null assignment provided for notification.");
        }
        LOGGER.log(Level.INFO, "Notifying student {0} about assignment ID: {1}", new Object[]{studentId, assignment.getId()});
        try {
            notifierAgent.notifyStudent(studentId, assignment);
            LOGGER.log(Level.INFO, "Student {0} notified successfully.", studentId);
        } catch (Exception e) {
             LOGGER.log(Level.SEVERE, "Failed to notify student " + studentId + " for assignment ID: " + assignment.getId(), e);
             throw e; // Re-throw
        }
    }
}