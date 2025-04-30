package com.dssv.logic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.util.Collections;

import com.dssv.pojos.*;
import com.dssv.gemini.GeminiApiClient;

public class AssignmentGenerator {


      public static String[] extractDescCodeTests(String jsonResponse) throws IOException {
        // 1) Protect already-escaped "\n", then escape any literal newlines
        String fixed = jsonResponse
            .replaceAll("\\\\n", "\\\\\\\\n")    // protect existing escapes :contentReference[oaicite:0]{index=0}
            .replaceAll("\\r?\\n", "\\\\n");     // escape raw LF and CRLF sequences

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(fixed);   // parse into JsonNode :contentReference[oaicite:1]{index=1}

        String desc  = root.path("description").asText("");    // asText() for value nodes
        String code  = root.path("code").asText("");
        String tests = root.path("testCases").toString();     // JsonNode.toString() emits valid JSON :contentReference[oaicite:2]{index=2}

        return new String[]{ desc, code, tests };
    }


    // Assume escapeJsonPrompt exists and works correctly
    private static String escapeJsonPrompt(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
                    // Add other escapes as needed
    }

    // Use Jackson ObjectMapper for robust parsing
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generates a personalized assignment using previous feedback.
     */
    public static Assignment generatePersonalizedAssignment(
            String studentId, List<Feedback> previousFeedbacks, // << CHANGED: Now takes List<Feedback>
            String pdfAnalysis, String retrievedContext, String topicOfInterest,
            GeminiApiClient geminiClient, String modelId) throws Exception {

        // --- Construct a detailed prompt for Gemini ---
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Generate a personalized programming assignment focused on '")
                     .append(escapeJsonPrompt(topicOfInterest != null ? topicOfInterest : "the provided context"))
                     .append("' for student ID '").append(escapeJsonPrompt(studentId)).append("'.\n");

        promptBuilder.append("Assignment Requirements:\n")
                     .append("- Should be in Python ONLY.\n")
                     .append("- Difficulty should be appropriate, considering previous feedback.\n") // << CHANGED: Mention feedback
                     .append("- Provide a clear problem description.\n")
                     .append("- Include Python starter code or a required function signature.\n")
                     .append("- Provide 2-3 clear Python-compatible test cases (e.g., simple assert statements or input/expected output pairs).\n")
                     .append("- Format the output STRICTLY as a single JSON object with keys: 'description' (string), 'code' (string containing Python starter code), 'testCases' (string containing Python test cases or descriptions).\n\n");


        if (pdfAnalysis != null && !pdfAnalysis.isBlank()) {
            promptBuilder.append("Context from provided document analysis:\n")
                         .append(escapeJsonPrompt(pdfAnalysis.substring(0, Math.min(pdfAnalysis.length(), 500)))) // Limit context size
                         .append("\n\n");
        }

        if (retrievedContext != null && !retrievedContext.isBlank()) {
              promptBuilder.append("Additional retrieved context:\n")
                           .append(escapeJsonPrompt(retrievedContext.substring(0, Math.min(retrievedContext.length(), 500)))) // Limit context size
                           .append("\n\n");
        }

        // --- Include Previous Feedback ---  << CHANGED SECTION >>
        if (previousFeedbacks != null && !previousFeedbacks.isEmpty()) {
            promptBuilder.append("Consider the student's previous assignment feedback (most recent first):\n");
            // Summarize feedback - avoid sending large amounts of text
            int feedbackLimit = Math.min(previousFeedbacks.size(), 3); // Limit history
            for (int i = 0; i < feedbackLimit; i++) {
                // Assuming feedbacks might be sorted newest first already by the fetch method.
                // If not, you might want to sort them by timestamp here.
                Feedback fb = previousFeedbacks.get(i);
                promptBuilder.append("- Assignment ID: ").append(escapeJsonPrompt(fb.getAssignmentId()));
                if (fb.getAccuracy() != null) {
                    promptBuilder.append(", Accuracy: ").append(fb.getAccuracy()).append("%");
                }
                String comments = fb.getcomments();
                if (comments != null && !comments.isBlank()) {
                     promptBuilder.append(", Feedback: ").append(escapeJsonPrompt(comments.substring(0, Math.min(comments.length(), 70)))).append("..."); // Limit feedback text length
                }
                 if (fb.getError() != null && !fb.getError().isBlank()) {
                    promptBuilder.append(", Error Noted: Yes"); // Just indicate if there was an error
                }
                promptBuilder.append("\n");
            }
            promptBuilder.append("\n");
        } else {
             promptBuilder.append("No previous feedback available for this student.\n\n");
        }
        // --- End of Changed Section ---

        // Ensure the final instruction is clear
        promptBuilder.append("Generate the JSON output containing 'description', 'code', and 'testCases':");

        String prompt = promptBuilder.toString();
        // Consider logging the full prompt if needed, but be mindful of length/PII
        System.out.println("AssignmentGenerator (Personalized) Prompt for student " + studentId + " (truncated):\n" + prompt.substring(0, Math.min(prompt.length(), 1000)) + "...");

        // Configure Gemini call for JSON output
        Map<String, Object> output_json_config = Map.of("response_mime_type", "application/json");

        // --- Call Gemini ---
        String jsonResponse;
        try {
            jsonResponse = geminiClient.generateTextWithConfig(modelId, prompt, output_json_config);
            // Log the raw response carefully, might contain PII or be very long
             System.out.println("AssignmentGenerator (Personalized) Raw Response from Gemini:\n" + jsonResponse.substring(0, Math.min(jsonResponse.length(), 500)) + "...");
        } catch (Exception e) {
            System.err.println("AssignmentGenerator ERROR: Failed to call Gemini API: " + e.getMessage());
            throw new Exception("Failed to generate assignment via Gemini API", e); // Re-throw
        }


        // --- Parse the JSON response using Jackson ---
        Assignment newAssignment = new Assignment();
        newAssignment.setId("temp-" + System.currentTimeMillis()); // Temporary ID, final one set later
        newAssignment.setStudentIds(Collections.singletonList(studentId)); // Set the student ID

        try {
            // Attempt to clean potential markdown ```json ... ``` wrappers if Gemini adds them
            String cleanedJsonResponse = jsonResponse.trim().replaceFirst("^```json", "").replaceFirst("```$", "").trim();

            JsonNode rootNode = objectMapper.readTree(cleanedJsonResponse);

            String desc = rootNode.path("description").asText("Error: Description missing in AI response.");
            String code = rootNode.path("code").asText("// Error: Starter code missing in AI response.");
            String tests = rootNode.path("testCases").asText("# Error: Test cases missing in AI response.");

            newAssignment.setDescription(desc);
            newAssignment.setCode(code);
            newAssignment.setTestCases(tests);

        } catch (Exception e) {
            System.err.println("AssignmentGenerator ERROR: Failed to parse Gemini JSON response for personalized assignment. Response was: " + jsonResponse + "\nError: " + e.getMessage());
            // Set error state in the assignment object
            newAssignment.setDescription("Error generating assignment: Failed to parse AI response.");
            newAssignment.setCode("// Error parsing AI response");
            newAssignment.setTestCases("// Error parsing AI response");
            // Optionally, keep the raw response in a field if needed for debugging, e.g. newAssignment.setSources(jsonResponse);
             newAssignment.setId("error-" + System.currentTimeMillis());
             // Decide if you want to throw an exception here or return the error assignment
             // throw new Exception("Failed to parse assignment generated by AI", e);
        }

        return newAssignment;
    }

    public static Assignment generateGroupAssignment(
            List<String> studentIds, String pdfAnalysis, String retrievedContext, String topicOfInterest,
            GeminiApiClient geminiClient, String modelId) throws Exception {

         // --- Construct a detailed prompt for Gemini ---
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Generate a standard programming assignment suitable for a group, focused on '")
                     .append(escapeJsonPrompt(topicOfInterest != null ? topicOfInterest : "the provided context"))
                     .append("'.\n");

        promptBuilder.append("Assignment Requirements:\n")
                     .append("- Provide a clear problem description suitable for intermediate learners.\n")
                     .append("- Include starter code or a required structure (e.g., class structure in Python).\n")
                     .append("- Provide 3-4 clear test cases (input and expected output).\n")
                     .append("- Format the output as a JSON object with keys: 'description', 'code', 'testCases'.\n\n");


         if (pdfAnalysis != null && !pdfAnalysis.isBlank()) {
            promptBuilder.append("Context from provided document analysis:\n")
                         .append(escapeJsonPrompt(pdfAnalysis.substring(0, Math.min(pdfAnalysis.length(), 500)))) // Limit context size
                         .append("\n\n");
        }

        if (retrievedContext != null && !retrievedContext.isBlank()) {
             promptBuilder.append("Additional retrieved context:\n")
                         .append(escapeJsonPrompt(retrievedContext.substring(0, Math.min(retrievedContext.length(), 500)))) // Limit context size
                         .append("\n\n");
        }

        promptBuilder.append("Generate the JSON output now:");

        String prompt = promptBuilder.toString();
        System.out.println("AssignmentGenerator (Group) Prompt (start):\n" + prompt.substring(0, Math.min(prompt.length(), 200)) + "...");


        // --- Call Gemini ---
        Map<String, Object> output_json_config = Map.of("response_mime_type", "application/json");
        // --- Call Gemini ---
        String jsonResponse = geminiClient.generateTextWithConfig(modelId, prompt, output_json_config);
        System.out.println("AssignmentGenerator (Group) Response:\n" + jsonResponse);


        // --- Parse the JSON response (Use a robust JSON library!) ---
         Assignment newAssignment = new Assignment();
         // Group assignments might not have a studentId initially, DB associates later
         try {
               String[] parts = extractDescCodeTests(jsonResponse);
               String desc  = parts[0];
               String code  = parts[1];
               String tests = parts[2]; 

               newAssignment.setId("temp-" + System.currentTimeMillis());
               newAssignment.setStudentIds(studentIds);
               // Set the extracted values
               newAssignment.setDescription(desc);
               newAssignment.setCode(code);
               newAssignment.setTestCases(tests);

         } catch (Exception e) {
              System.err.println("Failed to parse Gemini JSON response for group assignment: " + e.getMessage());
              newAssignment.setDescription("Error generating group assignment details from AI response.");
              newAssignment.setCode("// Error");
              newAssignment.setTestCases("// Error");
              newAssignment.setId("error-group-" + System.currentTimeMillis());
         }

        return newAssignment;
    }

}