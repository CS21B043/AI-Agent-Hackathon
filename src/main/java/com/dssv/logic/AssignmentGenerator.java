package com.dssv.logic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import com.dssv.pojos.*;
import com.dssv.gemini.GeminiApiClient;

public class AssignmentGenerator {

     // Helper to escape JSON strings for prompts
     private static String escapeJsonPrompt(String input) {
         if (input == null) return "";
         return input.replace("\\", "\\\\").replace("\"", "\\\"");
     }

    public static Assignment generatePersonalizedAssignment(
            String studentId, List<Assignment> previousAssignments,
            String pdfAnalysis, String retrievedContext, String topicOfInterest,
            GeminiApiClient geminiClient, String modelId) throws Exception {

        // --- Construct a detailed prompt for Gemini ---
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Generate a personalized programming assignment focused on '")
                     .append(escapeJsonPrompt(topicOfInterest != null ? topicOfInterest : "the provided context"))
                     .append("' for student ID '").append(escapeJsonPrompt(studentId)).append("'.\n");

        promptBuilder.append("Assignment Requirements:\n")
                     .append("- Difficulty should be appropriate, considering previous work.\n")
                     .append("- Provide a clear problem description.\n")
                     .append("- Include starter code or a required structure (e.g., function signature in Python).\n")
                     .append("- Provide 2-3 clear test cases (input and expected output).\n")
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

        if (previousAssignments != null && !previousAssignments.isEmpty()) {
            promptBuilder.append("Consider the student's previous assignments (briefly):\n");
            // Summarize or list titles/topics - avoid sending large amounts of old code
            for (int i = 0; i < Math.min(previousAssignments.size(), 3); i++) { // Limit history
                 Assignment prev = previousAssignments.get(i);
                 promptBuilder.append("- ID: ").append(escapeJsonPrompt(prev.getId()))
                              .append(", Desc: ").append(escapeJsonPrompt(prev.getDescription().substring(0, Math.min(prev.getDescription().length(), 50)))).append("...\n");
            }
            promptBuilder.append("\n");
        }

     //    promptBuilder.append("Generate the JSON output now:");

        String prompt = promptBuilder.toString();
        System.out.println("AssignmentGenerator (Personalized) Prompt (start):\n" + prompt.substring(0, Math.min(prompt.length(), 20000)) + "...");
        Map<String, Object> output_json_config = Map.of("response_mime_type", "application/json");
        // --- Call Gemini ---
        String jsonResponse = geminiClient.generateTextWithConfig(modelId, prompt, output_json_config);
        System.out.println("AssignmentGenerator (Personalized) Response:\n" + jsonResponse);


        // --- Parse the JSON response (CRITICAL: Use a robust JSON library like Jackson/Gson) ---
        // Basic parsing for demonstration - HIGHLY RECOMMENDED to use a library
        Assignment newAssignment = new Assignment();
        try{
          ObjectMapper mapper = new ObjectMapper();
          String fixed = jsonResponse
            // first, protect existing \n so we don’t double-escape
            .replaceAll("\\\\n", "\\\\\\\\n")
            // then escape all literal newlines
            .replaceAll("\\r?\\n", "\\\\n");

          JsonNode root = mapper.readTree(fixed);

          // Safely extract fields (returns empty string if missing)
          String desc  = root.path("description").asText("");
          String code  = root.path("code").asText("");

          JsonNode testsNode = root.path("testCases");
            // if (testsNode.isArray()) {
            //     for (JsonNode testCase : testsNode) {
            //         String inputPart  = testCase.path("input").toString();
            //         String expected   = testCase.path("expectedOutput").asText();
            //         // … process each testCase node …
            //     }
            // }
          String tests = testsNode.toString(); 

          newAssignment.setId("temp-" + System.currentTimeMillis());
          newAssignment.setStudentId(studentId);
          // Set the extracted values
          newAssignment.setDescription(desc);
          newAssignment.setCode(code);
          newAssignment.setTestCases(tests);
        } catch (Exception e) {
            System.err.println("Failed to parse Gemini JSON response for personalized assignment: " + e.getMessage());
            // Return a default/error assignment
            newAssignment.setDescription("Error generating assignment details from AI response.");
            newAssignment.setCode("// Error");
            newAssignment.setTestCases("// Error");
             newAssignment.setId("error-" + System.currentTimeMillis());
        }

        return newAssignment;
    }

    public static Assignment generateGroupAssignment(
            String pdfAnalysis, String retrievedContext, String topicOfInterest,
            GeminiApiClient geminiClient, String modelId) throws Exception {

         // --- Construct a detailed prompt for Gemini ---
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Generate a standard programming assignment suitable for a group, focused on '")
                     .append(escapeJsonPrompt(topicOfInterest != null ? topicOfInterest : "the provided context"))
                     .append("'.\n");

        promptBuilder.append("Assignment Requirements:\n")
                     .append("- Provide a clear problem description suitable for intermediate learners.\n")
                     .append("- Include starter code or a required structure (e.g., class structure in Java).\n")
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
        String jsonResponse = geminiClient.generateText(modelId, prompt);
        System.out.println("AssignmentGenerator (Group) Response:\n" + jsonResponse);


        // --- Parse the JSON response (Use a robust JSON library!) ---
         Assignment newAssignment = new Assignment();
         // Group assignments might not have a studentId initially, DB associates later
         try {
               ObjectMapper mapper = new ObjectMapper();
               JsonNode root = mapper.readTree(jsonResponse);

               // Safely extract fields (returns empty string if missing)
               String desc  = root.path("description").asText("");
               String code  = root.path("code").asText("");
               String tests = root.path("testCases").asText("");

               newAssignment.setId("temp-" + System.currentTimeMillis());
               newAssignment.setStudentId("Teacher-Generated"); // Placeholder 
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