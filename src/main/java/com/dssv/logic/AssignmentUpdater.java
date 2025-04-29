package com.dssv.logic;

import com.dssv.pojos.*;
import com.dssv.gemini.GeminiApiClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AssignmentUpdater {

     private static String escapeJsonPrompt(String input) {
          if (input == null) return "";
          return input.replace("\\", "\\\\").replace("\"", "\\\"");
     }

    public static Assignment updateAssignment(
            Assignment existingAssignment, Feedback feedback,
            GeminiApiClient geminiClient, String modelId) throws Exception {

        // --- Construct prompt for Gemini ---
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Update the following programming assignment based on the teacher's feedback.\n\n");

        promptBuilder.append("Original Assignment:\n")
                     .append("Description: ").append(escapeJsonPrompt(existingAssignment.getDescription())).append("\n")
                     .append("Code: ").append(escapeJsonPrompt(existingAssignment.getCode())).append("\n")
                     .append("Test Cases: ").append(escapeJsonPrompt(existingAssignment.getTestCases())).append("\n\n");

        promptBuilder.append("Teacher Feedback:\n")
                     .append(escapeJsonPrompt(feedback.getComments())).append("\n\n");

        promptBuilder.append("Generate the updated assignment.\n")
                      .append("Format the output as a JSON object with the updated keys: 'description', 'code', 'testCases'.\n")
                      .append("Ensure the changes reflect the feedback provided.\n\n");

        promptBuilder.append("Generate the updated JSON output now:");

        String prompt = promptBuilder.toString();
        System.out.println("AssignmentUpdater Prompt (start):\n" + prompt.substring(0, Math.min(prompt.length(), 200)) + "...");


        // --- Call Gemini ---
        String jsonResponse = geminiClient.generateText(modelId, prompt);
         System.out.println("AssignmentUpdater Response:\n" + jsonResponse);


        // --- Parse response and update the existing assignment object (Use a JSON library!) ---
         // Or create a new one - let's update the existing one here.
         try {
              ObjectMapper mapper = new ObjectMapper();
              JsonNode root = mapper.readTree(jsonResponse);

               // Safely extract fields (returns empty string if missing)
              String desc  = root.path("description").asText("");
              String code  = root.path("code").asText("");
              String tests = root.path("testCases").asText("");

              // Update only if generation was successful (value is not null)
              if (desc != null) existingAssignment.setDescription(desc);
              if (code != null) existingAssignment.setCode(code);
              if (tests != null) existingAssignment.setTestCases(tests);

         } catch (Exception e) {
             System.err.println("Failed to parse Gemini JSON response for assignment update: " + e.getMessage());
             // Optionally add a note about the failure to the description or handle error
             existingAssignment.setDescription(existingAssignment.getDescription() + "\n\n[Update based on feedback failed due to AI parsing error]");
         }


        // Return the modified existing assignment object
        return existingAssignment;
    }
}