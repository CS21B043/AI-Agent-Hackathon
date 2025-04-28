/*
Current focus is only on Code. So all assignments are code related with test cases.
The teacher agent is responsible for creating personalized assignments for students based on their previous assignments and feedback from the teacher. It interacts with the Gemini API to analyze PDFs and perform multi-turn chats.

Functionality:
1) /Create_Solo: Create a new personalized assignment for a student. Requested by student. Input can be PDF or Textual Description of Topic of Interest(ToI).
2) /Create_Group: Create a new assignment for a group of students. May not be personalized for the group. Requested by teacher. Again, input can be PDF or Textual Description of Topic of Interest(ToI).
3) /Update: Update an existing assignment based on feedback from the teacher. Human In The Loop (HITL) process.  
4) fetch_from_db: Helper function to fetch student's previous assignments from the database. This is used to create a new personalized assignment for the student.
5) push_to_db: Helper function to push the new assignment to the database. This is used to store the new personalized assignment along with test cases.
6) retrieve: This will call the retriever agent to retrieve related assignments and theoretical information from GitHub and Search Engines like Brave and DuckDuckGo. This is used to provide more context to the teacher agent while creating assignments.
7) Notify: This will call the notifier agent to notify the teacher about the new assignment. The teacher can then verify the theory and the assignment and either update it or approve it. Human In The Loop (HITL) process.
8) Notify_Student: This will call the notifier agent to notify the student about the new assignment. The student can then access and solve the assignment.
9) Discuss: The student can discuss the assignment with the teacher agent. This is a multi-turn chat process. The student can ask questions and learn the theory behind the assignment. The student can also provide feedback on the assignment.
*/

package com.dssv.agents;

import com.dssv.gemini.GeminiApiClient;
import com.dssv.gemini.GeminiModelInfo; 

import com.dssv.database.DatabaseClient; 
import com.dssv.agents.NotifierAgent;    
import com.dssv.agents.RetrieverAgent;   
import com.dssv.pojos.Assignment;         
import com.dssv.pojos.Feedback;           
import com.dssv.logic.AssignmentGenerator; 
import com.dssv.logic.AssignmentUpdater;   

import java.util.List;
import java.util.Objects;
import java.io.IOException; // For potential exceptions from GeminiApiClient

public class TeacherAgent {

    private final GeminiApiClient geminiClient;
    private final DatabaseClient databaseClient;
    private final NotifierAgent notifierAgent;
    private final RetrieverAgent retrieverAgent;
    // Define a default model ID to use, can be configurable
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
     * Input can be PDF bytes and/or a textual description of the Topic of Interest (ToI).
     *
     * @param studentId       The ID of the student.
     * @param pdfBytes        Optional byte array of a PDF document for context.
     * @param topicOfInterest Optional textual description of the desired topic.
     * @return The newly created Assignment.
     * @throws Exception If any error occurs during the process.
     */
    public Assignment createSoloAssignment(String studentId, byte[] pdfBytes, String topicOfInterest) throws Exception {
        if (studentId == null || studentId.isBlank()) {
            throw new IllegalArgumentException("Student ID cannot be null or empty.");
        }
        if (pdfBytes == null && (topicOfInterest == null || topicOfInterest.isBlank())) {
             throw new IllegalArgumentException("Either PDF content or a Topic of Interest must be provided.");
        }

        System.out.println("TeacherAgent: Creating solo assignment for student: " + studentId);

        // 1. Fetch student's previous assignments for personalization context
        List<Assignment> previousAssignments = fetchFromDb(studentId);
        System.out.println("TeacherAgent: Fetched " + (previousAssignments == null ? 0 : previousAssignments.size()) + " previous assignments.");

        // 2. Analyze PDF using Gemini if provided
        String pdfAnalysisText = null;
        if (pdfBytes != null && pdfBytes.length > 0) {
            System.out.println("TeacherAgent: Analyzing provided PDF...");
            try {
                // Use Gemini to understand the PDF content in the context of creating an assignment
                String pdfPrompt = "Analyze the key concepts and potential programming assignment ideas from the provided document content. Focus on topics suitable for a coding assignment.";
                 // Use the multimodal capability of the Gemini API Client
                pdfAnalysisText = geminiClient.generateContentWithDocumentBytes(
                    defaultGeminiModelId, // Use a model capable of handling documents
                    pdfPrompt,
                    "application/pdf", // Assuming the bytes are always PDF
                    pdfBytes
                );
                 System.out.println("TeacherAgent: PDF analysis result obtained.");
                 // You might want to log pdfAnalysisText partially for debugging if needed
            } catch (IOException | InterruptedException e) {
                System.err.println("TeacherAgent: Error analyzing PDF with Gemini: " + e.getMessage());
                // Decide if this is a fatal error or if we can proceed without PDF analysis
                throw new Exception("Failed to analyze PDF content.", e);
            }
        }

        // 3. Retrieve related context using RetrieverAgent if ToI provided
        String retrievedContext = null;
        if (topicOfInterest != null && !topicOfInterest.isBlank()) {
            System.out.println("TeacherAgent: Retrieving context for ToI: " + topicOfInterest);
            try {
                retrievedContext = retrieverAgent.retrieve(topicOfInterest);
                 System.out.println("TeacherAgent: Retrieved context.");
                 // Log retrievedContext partially if needed
            } catch (Exception e) {
                 System.err.println("TeacherAgent: Error retrieving context: " + e.getMessage());
                 // Decide if this is fatal or proceed without external context
                 // For now, we'll proceed without it if retrieval fails
                 System.err.println("TeacherAgent: Proceeding without retrieved context due to error.");
            }
        }

        // 4. Generate Personalized Assignment using AssignmentGenerator (which uses Gemini)
        System.out.println("TeacherAgent: Generating personalized assignment...");
        Assignment newAssignment = AssignmentGenerator.generatePersonalizedAssignment(
                studentId,
                previousAssignments,
                pdfAnalysisText, // Result from PDF analysis
                retrievedContext, // Context from retriever
                topicOfInterest, // Original ToI for reference
                geminiClient,     // Pass the client for generation
                defaultGeminiModelId
        );
         System.out.println("TeacherAgent: Assignment generated.");

        // 5. Push the new assignment to the database
        System.out.println("TeacherAgent: Saving assignment to DB...");
        pushToDb(studentId, newAssignment);
         System.out.println("TeacherAgent: Assignment saved.");

        // 6. Notify the Teacher for review (HITL)
        System.out.println("TeacherAgent: Notifying teacher...");
        notifyTeacher(newAssignment);
         System.out.println("TeacherAgent: Teacher notified.");

        // 7. Return the created assignment (student notification might happen after teacher approval)
        return newAssignment;
    }

    /**
     * /Create_Group: Creates a new assignment for a group of students.
     * May not be personalized. Input can be PDF bytes and/or a textual description of the Topic of Interest (ToI).
     *
     * @param studentIds      List of student IDs in the group.
     * @param pdfBytes        Optional byte array of a PDF document for context.
     * @param topicOfInterest Optional textual description of the desired topic.
     * @return The newly created Assignment (same for all students in the group).
     * @throws Exception If any error occurs during the process.
     */
    public Assignment createGroupAssignment(List<String> studentIds, byte[] pdfBytes, String topicOfInterest) throws Exception {
        if (studentIds == null || studentIds.isEmpty()) {
            throw new IllegalArgumentException("Student IDs list cannot be null or empty.");
        }
         if (pdfBytes == null && (topicOfInterest == null || topicOfInterest.isBlank())) {
             throw new IllegalArgumentException("Either PDF content or a Topic of Interest must be provided.");
        }

        System.out.println("TeacherAgent: Creating group assignment for " + studentIds.size() + " students.");

        // 1. Analyze PDF using Gemini if provided (same as solo)
        String pdfAnalysisText = null;
        if (pdfBytes != null && pdfBytes.length > 0) {
             System.out.println("TeacherAgent: Analyzing provided PDF for group assignment...");
            try {
                String pdfPrompt = "Analyze the key concepts and potential programming assignment ideas from the provided document content. Focus on topics suitable for a general group coding assignment.";
                pdfAnalysisText = geminiClient.generateContentWithDocumentBytes(
                    defaultGeminiModelId,
                    pdfPrompt,
                    "application/pdf",
                    pdfBytes
                );
                System.out.println("TeacherAgent: PDF analysis result obtained for group.");
            } catch (IOException | InterruptedException e) {
                System.err.println("TeacherAgent: Error analyzing PDF with Gemini for group: " + e.getMessage());
                throw new Exception("Failed to analyze PDF content for group assignment.", e);
            }
        }

        // 2. Retrieve related context using RetrieverAgent if ToI provided (same as solo)
        String retrievedContext = null;
        if (topicOfInterest != null && !topicOfInterest.isBlank()) {
            System.out.println("TeacherAgent: Retrieving context for group ToI: " + topicOfInterest);
             try {
                retrievedContext = retrieverAgent.retrieve(topicOfInterest);
                 System.out.println("TeacherAgent: Retrieved context for group.");
             } catch (Exception e) {
                 System.err.println("TeacherAgent: Error retrieving context for group: " + e.getMessage());
                 System.err.println("TeacherAgent: Proceeding without retrieved context due to error.");
             }
        }

        // 3. Generate Group Assignment using AssignmentGenerator (which uses Gemini)
        System.out.println("TeacherAgent: Generating group assignment...");
        // Group assignments might not need personalization factors like previous assignments
        Assignment newAssignment = AssignmentGenerator.generateGroupAssignment(
                pdfAnalysisText,    // Result from PDF analysis
                retrievedContext,   // Context from retriever
                topicOfInterest,    // Original ToI for reference
                geminiClient,       // Pass the client for generation
                defaultGeminiModelId
        );
         System.out.println("TeacherAgent: Group assignment generated.");

        // 4. Push the new assignment to the database for EACH student
        System.out.println("TeacherAgent: Saving group assignment to DB for each student...");
        for (String studentId : studentIds) {
            if (studentId != null && !studentId.isBlank()) {
                // Important: Ensure the Assignment object doesn't have a studentId set yet,
                // or clone it, or handle how DB associates it. Assuming pushToDb handles this.
                pushToDb(studentId, newAssignment); // Push the *same* assignment object reference (or clone if needed)
            } else {
                 System.err.println("TeacherAgent: Skipping invalid student ID in group list.");
            }
        }
         System.out.println("TeacherAgent: Group assignment saved for all valid students.");


        // 5. Notify the Teacher for review (HITL) - notify once for the group assignment
        System.out.println("TeacherAgent: Notifying teacher about group assignment...");
        notifyTeacher(newAssignment); // Notify about the assignment content
        System.out.println("TeacherAgent: Teacher notified about group assignment.");

        // 6. Return the created assignment
        return newAssignment;
    }

    /**
     * /Update: Updates an existing assignment based on feedback from the teacher.
     *
     * @param assignmentId The ID of the assignment to update.
     * @param feedback     The feedback provided by the teacher.
     * @return The updated Assignment.
     * @throws Exception If any error occurs during the process.
     */
    public Assignment updateAssignment(String assignmentId, Feedback feedback) throws Exception {
        if (assignmentId == null || assignmentId.isBlank()) {
            throw new IllegalArgumentException("Assignment ID cannot be null or empty.");
        }
        if (feedback == null) {
            throw new IllegalArgumentException("Feedback cannot be null.");
        }

        System.out.println("TeacherAgent: Updating assignment ID: " + assignmentId);

        // 1. Fetch the existing assignment from the database
        System.out.println("TeacherAgent: Fetching existing assignment from DB...");
        Assignment existingAssignment = databaseClient.fetchAssignmentById(assignmentId);
        if (existingAssignment == null) {
            throw new Exception("Assignment with ID " + assignmentId + " not found.");
        }
         System.out.println("TeacherAgent: Existing assignment fetched.");

        // 2. Update the assignment using AssignmentUpdater (which uses Gemini)
        System.out.println("TeacherAgent: Applying updates based on feedback...");
        Assignment updatedAssignment = AssignmentUpdater.updateAssignment(
            existingAssignment,
            feedback,
            geminiClient, // Pass the client for update generation
            defaultGeminiModelId
        );
        // The updater might return the same object modified, or a new one.
        // Let's assume it returns the conceptually updated assignment.
         System.out.println("TeacherAgent: Assignment updated based on feedback.");

        // 3. Push the updated assignment back to the database
        // We need the student ID associated with this assignment. Assuming Assignment object has it.
        if (updatedAssignment.getStudentId() == null || updatedAssignment.getStudentId().isBlank()) {
             // If the student ID isn't carried in the updated object, fetch it from the original
             // or modify updateAssignment logic/DB logic. Assuming it's present.
            String studentId = existingAssignment.getStudentId(); // Get from original if needed
            if(studentId == null || studentId.isBlank()) {
                 throw new Exception("Cannot determine student ID for assignment ID: " + assignmentId + " to save update.");
            }
             // Manually set it if needed, depends on how your objects/DB work
             // updatedAssignment.setStudentId(studentId);
             System.out.println("TeacherAgent: Saving updated assignment for student ID: " + studentId);
             pushToDb(studentId, updatedAssignment);

        } else {
             System.out.println("TeacherAgent: Saving updated assignment for student ID: " + updatedAssignment.getStudentId());
             pushToDb(updatedAssignment.getStudentId(), updatedAssignment);
        }
         System.out.println("TeacherAgent: Updated assignment saved.");


        // 4. Notify the Teacher again about the update (optional, maybe only notify student)
        System.out.println("TeacherAgent: Notifying teacher about the update...");
        notifyTeacher(updatedAssignment); // Notify teacher that update is complete
        System.out.println("TeacherAgent: Teacher notified about update.");

        // 5. Optionally notify the student now
        // notifyStudent(updatedAssignment.getStudentId(), updatedAssignment);

        // 6. Return the updated assignment
        return updatedAssignment;
    }

    // --- Helper Functions ---

    /**
     * fetch_from_db: Helper function to fetch student's previous assignments.
     */
    private List<Assignment> fetchFromDb(String studentId) throws Exception {
        // Delegate to the database client
        return databaseClient.fetchAssignmentsByStudentId(studentId);
    }

    /**
     * push_to_db: Helper function to push the new/updated assignment to the database.
     * Assumes the database client handles associating the assignment with the studentId.
     */
    private void pushToDb(String studentId, Assignment assignment) throws Exception {
        // Delegate to the database client
        databaseClient.saveAssignment(studentId, assignment);
    }

    // The retrieve method using retrieverAgent is called directly within create methods now.
    // private String retrieve(String query) throws Exception {
    //     return retrieverAgent.retrieve(query);
    // }

    /**
     * Notify: Helper to notify the teacher about a new or updated assignment for review.
     */
    private void notifyTeacher(Assignment assignment) throws Exception {
        // Delegate to the notifier agent
        notifierAgent.notifyTeacher(assignment);
    }

    /**
     * Notify_Student: Notifies the student about a new/updated assignment.
     * This might be called after teacher approval in a real workflow.
     */
    public void notifyStudent(String studentId, Assignment assignment) throws Exception {
        if (studentId == null || studentId.isBlank()) {
             System.err.println("TeacherAgent: Cannot notify student - invalid student ID provided.");
             return; // Or throw exception
        }
         if (assignment == null) {
              System.err.println("TeacherAgent: Cannot notify student " + studentId + " - assignment is null.");
              return; // Or throw exception
         }
         System.out.println("TeacherAgent: Notifying student " + studentId + " about assignment ID: " + assignment.getId());
        // Delegate to the notifier agent
        notifierAgent.notifyStudent(studentId, assignment);
         System.out.println("TeacherAgent: Student " + studentId + " notified.");
    }

     /**
     * Discuss: Handles multi-turn chat with a student about an assignment.
     * This requires managing conversation history.
     *
     * @param studentId The ID of the student initiating the discussion.
     * @param assignmentId The ID of the assignment being discussed.
     * @param studentMessage The student's current message/question.
     * @param conversationHistory The history of the conversation so far.
     * @return The agent's response.
     * @throws Exception If chat generation fails.
     */
    public String discuss(String studentId, String assignmentId, String studentMessage, List<Message> conversationHistory) throws Exception {
         if (studentId == null || studentId.isBlank() || assignmentId == null || assignmentId.isBlank() || studentMessage == null || studentMessage.isBlank()) {
            throw new IllegalArgumentException("Student ID, Assignment ID, and Student Message are required for discussion.");
        }
         if (conversationHistory == null) {
             throw new IllegalArgumentException("Conversation history cannot be null (can be empty).");
         }

         System.out.println("TeacherAgent: Handling discussion from student " + studentId + " about assignment " + assignmentId);

         // 1. Fetch assignment details (optional, but good for context)
         Assignment assignment = databaseClient.fetchAssignmentById(assignmentId);
         String assignmentContext = "";
         if (assignment != null) {
             assignmentContext = " The discussion is about the following assignment:\nTitle: " + assignment.getDescription() + "\nCode Snippet: " + assignment.getCode() + "\n";
         } else {
             assignmentContext = " The discussion is about assignment ID " + assignmentId + ", but its details couldn't be fetched.";
              System.err.println("TeacherAgent: Could not fetch details for assignment " + assignmentId + " during discussion.");
         }


         // 2. Construct the prompt/payload for Gemini's multi-turn chat
         // The payload structure depends heavily on how your `Message` POJO and Gemini API expect it.
         // Assuming a simple structure where we add the current message and maybe context.
         // You NEED to adapt this based on the actual expected JSON format for `generateMultiTurnChat`.
         // See `GeminiApiClient` for the structure it expects. It takes a pre-formatted JSON string.

         // Add the new user message to the history (assuming Message has role and parts)
         // conversationHistory.add(new Message("user", studentMessage)); // Adapt Message constructor

         // Build the JSON payload string expected by your GeminiApiClient.generateMultiTurnChat
         // This usually involves formatting the conversationHistory into a JSON array.
         // Let's create a very basic example payload - THIS NEEDS REFINEMENT based on Message class and API docs.
         StringBuilder jsonPayloadBuilder = new StringBuilder();
         jsonPayloadBuilder.append("{\"contents\": [");

         // Add system instructions/context
         jsonPayloadBuilder.append("{\"role\": \"system\", \"parts\":[{\"text\": \"You are a helpful teaching assistant. Answer the student's questions about their programming assignment clearly and concisely. Be encouraging.")
                      .append(escapeJsonString(assignmentContext)) // Add assignment context safely
                      .append("\"}]},");

         // Add previous messages
         for (int i = 0; i < conversationHistory.size(); i++) {
             Message msg = conversationHistory.get(i);
              // IMPORTANT: Replace with actual fields and structure of your Message class
             jsonPayloadBuilder.append("{\"role\": \"").append(escapeJsonString(msg.getRole())) // e.g., "user" or "model"
                           .append("\", \"parts\":[{\"text\": \"").append(escapeJsonString(msg.getText())) // e.g., the message content
                           .append("\"}]}");
             if (i < conversationHistory.size() - 1) {
                 jsonPayloadBuilder.append(",");
             }
         }
         // Add the latest student message (if not already added to history)
         if (!conversationHistory.isEmpty()) jsonPayloadBuilder.append(","); // Add comma if history wasn't empty
         jsonPayloadBuilder.append("{\"role\": \"user\", \"parts\":[{\"text\": \"").append(escapeJsonString(studentMessage)).append("\"}]}");


         jsonPayloadBuilder.append("],");
         // Add generation config if needed (optional)
         jsonPayloadBuilder.append("\"generationConfig\": {\"temperature\": 0.7}"); // Example config
         jsonPayloadBuilder.append("}");

         String chatPayload = jsonPayloadBuilder.toString();
         System.out.println("TeacherAgent: Sending chat payload to Gemini:\n" + chatPayload.substring(0, Math.min(chatPayload.length(), 200)) + "..."); // Log start of payload

         // 3. Call Gemini's multi-turn chat endpoint
         String responseText;
         try {
              // Using the specific method for chat payloads
             responseText = geminiClient.generateMultiTurnChat(defaultGeminiModelId, chatPayload);
         } catch (IOException | InterruptedException e) {
             System.err.println("TeacherAgent: Error during chat generation with Gemini: " + e.getMessage());
             throw new Exception("Failed to get response for discussion.", e);
         }

         // 4. (Optional) Update conversation history with the model's response
         // conversationHistory.add(new Message("model", responseText));

         System.out.println("TeacherAgent: Received chat response from Gemini.");
         return responseText;
     }

    // Helper for escaping JSON strings within the discuss method payload construction
    private String escapeJsonString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}