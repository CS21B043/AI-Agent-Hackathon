package com.dssv.logic
class AssignmentGenerator {

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
                     .append("- Include starter code or a required structure (e.g., function signature in Java).\n")
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

        promptBuilder.append("Generate the JSON output now:");

        String prompt = promptBuilder.toString();
         System.out.println("AssignmentGenerator (Personalized) Prompt (start):\n" + prompt.substring(0, Math.min(prompt.length(), 200)) + "...");

        // --- Call Gemini ---
        String jsonResponse = geminiClient.generateText(modelId, prompt);
        System.out.println("AssignmentGenerator (Personalized) Response:\n" + jsonResponse);


        // --- Parse the JSON response (CRITICAL: Use a robust JSON library like Jackson/Gson) ---
        // Basic parsing for demonstration - HIGHLY RECOMMENDED to use a library
        Assignment newAssignment = new Assignment();
        newAssignment.setStudentId(studentId); // Set the student ID
        try {
             // Example using simple string search (Fragile!)
            String desc = extractJsonValue(jsonResponse, "description");
            String code = extractJsonValue(jsonResponse, "code");
            String tests = extractJsonValue(jsonResponse, "testCases");

            newAssignment.setDescription(desc != null ? desc : "Assignment description generation failed.");
            newAssignment.setCode(code != null ? code : "// Starter code generation failed.");
            newAssignment.setTestCases(tests != null ? tests : "// Test case generation failed.");
            // Generate a temporary ID or let DB handle it
             newAssignment.setId("temp-" + System.currentTimeMillis());

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
              String desc = extractJsonValue(jsonResponse, "description");
              String code = extractJsonValue(jsonResponse, "code");
              String tests = extractJsonValue(jsonResponse, "testCases");

              newAssignment.setDescription(desc != null ? desc : "Group assignment description generation failed.");
              newAssignment.setCode(code != null ? code : "// Group starter code generation failed.");
              newAssignment.setTestCases(tests != null ? tests : "// Group test case generation failed.");
              newAssignment.setId("temp-group-" + System.currentTimeMillis()); // Temporary ID

         } catch (Exception e) {
              System.err.println("Failed to parse Gemini JSON response for group assignment: " + e.getMessage());
              newAssignment.setDescription("Error generating group assignment details from AI response.");
              newAssignment.setCode("// Error");
              newAssignment.setTestCases("// Error");
              newAssignment.setId("error-group-" + System.currentTimeMillis());
         }

        return newAssignment;
    }

     // VERY Basic JSON value extractor - Replace with Jackson/Gson
     private static String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\": \"";
        int start = json.indexOf(searchKey);
        if (start == -1) {
             searchKey = "\"" + key + "\":"; // Try without space for numbers/booleans/nested objects
             start = json.indexOf(searchKey);
             if (start == -1) return null; // Key not found
              start += searchKey.length();
              // Find the end based on next comma or brace (simplistic)
              int endComma = json.indexOf(',', start);
              int endBrace = json.indexOf('}', start);
              int end = -1;
              if (endComma != -1 && endBrace != -1) end = Math.min(endComma, endBrace);
              else if (endComma != -1) end = endComma;
              else if (endBrace != -1) end = endBrace;
              else end = json.length(); // End of string

              if(end == -1) return null;
              String val = json.substring(start, end).trim();
              if (val.startsWith("\"") && val.endsWith("\"")) { // Handle string values found this way
                 return val.substring(1, val.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
              }
              return val; // Return as is (might be number, boolean, etc.)

        }
        start += searchKey.length();
        int end = json.indexOf("\"", start); // Find closing quote
        if (end == -1) return null; // Malformed
        // Basic unescaping
        return json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
    }
}