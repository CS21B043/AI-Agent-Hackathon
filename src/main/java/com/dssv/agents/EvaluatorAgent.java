/*
Text generation Payload should include both code execution and structured output(JSON) in the same payload.
         String jsonPayload = String.format(
             "{\"tools\": [{\"code_execution\": {}}], \"contents\": {\"parts\": {\"text\": \"%s\"}}}",
             escapeJsonString(prompt)
         );
-H 'Content-Type: application/json' \
-d '{"tools": [{"code_execution": {}}],
    "contents": [{
      "parts":[
        {"text": "List a few popular cookie recipes using this JSON schema:

            Recipe = {\"recipe_name\": str}
            Return: list[Recipe]"
        }
      ]
    }],
    "generationConfig": { "response_mime_type": "application/json" }
payload = process(req.prompt);
public String generateTextWithPayload(String modelId, String payload) throws IOException, InterruptedException {
1) evaluate: Should call the Gemini Code Execution API endpoint  client.generateTextFromPayload(defaultCodeModel, payload);
Sample Prompt: You are an evaluator, who evaluates assignments submitted by students. Write python code to run the student's code as is on the provided test cases and return the results. Strcuture the json with a short feedback and then the result(maybe in terms of accuracy %) and error(if any). ```python {student_submission}```. TestCases: {testcases}.  DO NOT CHANGE THE STUDENT'S CODE, EVALUATE IT AS IS."
Assignment Info:
public class Assignment {
    private String id;
    // Replaced single studentId with a list
    private List<String> studentIds;
    private String description;
    private String code; // Could be starter code, or expected structure
    private String testCases; // Could be text description or actual test code
    private String sources; //
Similarly create a pojo called Feedback with comments, accuracy etc...
This feedback should be stored in the database(empty constructor needed for Jackson ig) and then used to create personalized assignments for the students.
*/

package com.dssv.agents;

import com.dssv.pojos.*;
import com.dssv.database.DatabaseClient;
import com.dssv.gemini.GeminiApiClient; 
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EvaluatorAgent{

    private static final Logger LOGGER = Logger.getLogger(EvaluatorAgent.class.getName());
    private final DatabaseClient dbClient;
    private final GeminiApiClient geminiClient;
    private final String defaultCodeModel = "gemini-1.5-flash"; // Model ID for code execution
    private final ObjectMapper objectMapper; // For parsing JSON response

    public EvaluatorAgent(String apiKey,DatabaseClient dbClient) {
        this.dbClient = dbClient;
        this.geminiClient = new GeminiApiClient(apiKey); // Initialize Gemini API client
        this.objectMapper = new ObjectMapper(); // Initialize ObjectMapper
    }

    // Simple JSON string escaping
    private String escapeJsonString(String value) {
        if (value == null) return "";
        // Basic escaping for quotes, backslashes, and control characters
        return value.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

     // Helper to extract accuracy percentage
     private Double parseAccuracy(String accuracyStr) {
         if (accuracyStr == null || accuracyStr.isBlank()) {
             return null;
         }
         // Try to extract number, removing '%' if present
         Pattern pattern = Pattern.compile("(\\d+(\\.\\d+)?)");
         Matcher matcher = pattern.matcher(accuracyStr);
         if (matcher.find()) {
             try {
                 return Double.parseDouble(matcher.group(1));
             } catch (NumberFormatException e) {
                 LOGGER.log(Level.WARNING, "Could not parse accuracy string: " + accuracyStr, e);
                 return null;
             }
         }
         return null;
     }

    public String evaluate(String studentId, String assignmentId, String answer) throws Exception {
        LOGGER.log(Level.INFO, "EvaluatorAgent: Evaluating assignment {0} for student {1}", new Object[]{assignmentId, studentId});

        // 1. Fetch Assignment to get test cases
        Assignment assignment = dbClient.fetchAssignmentById(assignmentId);
        if (assignment == null) {
            throw new IllegalArgumentException("Assignment not found: " + assignmentId);
        }
        String testCases = assignment.getTestCases();
        if (testCases == null || testCases.isBlank()) {
             LOGGER.log(Level.WARNING, "No test cases found for assignment {0}. Evaluation might be incomplete.", assignmentId);
             // Decide how to proceed: throw error or allow evaluation without tests?
             // For now, let's proceed but the feedback might be less useful.
             testCases = "No test cases provided."; // Set a default placeholder
             // throw new IllegalArgumentException("Test cases are missing for assignment: " + assignmentId);
        }

        // 2. Construct the Prompt for Gemini Code Execution API
        String promptTemplate = "You are an evaluator, who evaluates assignments submitted by students.\n" +
        "Write and execute Python code to run the student's code submission as is against the provided test cases.\n" +
        "Your response MUST be a JSON object containing 'feedback', 'result' (which should include accuracy, e.g., \"80%%\" or \"Passed 3/5 tests\"), and 'error' (if any errors occurred during execution, otherwise null or empty).\n" +
        "Example JSON structure:\n" +
        "{\n" +
        "  \"feedback\": \"A short summary of the evaluation.\",\n" +
        "  \"result\": \"Accuracy: 90%%\",\n" +
        "  \"error\": null\n" +
        "}\n" +
        "or\n" +
        "{\n" +
        "  \"feedback\": \"Code failed on test case 2.\",\n" +
        "  \"result\": \"Accuracy: 40%%\",\n" +
        "  \"error\": \"IndexError: list index out of range on line 5\"\n" +
        "}\n" +
        "\n" +
        "Student's Code Submission:\n" +
        "```python\n" +
        "%s\n" +
        "```\n" +
        "\n" +
        "Test Cases:\n" +
        "```\n" +
        "%s\n" +
        "```\n" +
        "\n" +
        "DO NOT CHANGE THE STUDENT'S CODE, EVALUATE IT AS IS. Execute the tests and provide the results in the specified JSON format.";
        String prompt = String.format(promptTemplate,
                escapeJsonString(answer),      // Student's code
                escapeJsonString(testCases)    // Test cases
        );


        // 3. Create the JSON Payload for the API
        // Ensure the payload requests code execution AND structured JSON output
        String payload = String.format(
            "{\n" +
            "  \"tools\": [{\"code_execution\": {}}],\n" +
            "  \"contents\": [{\"parts\": [{\"text\": \"%s\"}]}],\n" +
            "  \"generationConfig\": { \"response_mime_type\": \"application/json\" }\n" +
            "}", escapeJsonString(prompt));


        // 4. Call the Gemini API
        LOGGER.log(Level.INFO, "EvaluatorAgent: Calling Gemini Code Execution API for assignment {0}", assignmentId);
        String jsonResponse;
        try {
            jsonResponse = geminiClient.generateTextFromPayload(defaultCodeModel, payload);
            LOGGER.log(Level.FINE, "EvaluatorAgent: Received raw response: {0}", jsonResponse);
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "EvaluatorAgent: Error calling Gemini API for assignment " + assignmentId, e);
            throw new RuntimeException("Failed to evaluate assignment due to API error", e);
        }

        // 5. Parse the JSON Response and Create Feedback Object
        Feedback feedback = new Feedback();
        feedback.setId("fb-" + UUID.randomUUID().toString()); // Generate unique ID
        feedback.setStudentId(studentId);
        feedback.setAssignmentId(assignmentId);
        feedback.setTimestamp(LocalDateTime.now());

        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            // Gemini might wrap the function call result. Adjust path if needed.
            // Example: If response is {"candidates": [{"content": {"parts": [{"functionCall": {...}}]}}]}
            // Or if it's directly {"candidates": [{"content": {"parts": [{"text": "{ \"feedback\": ... }"}]}}]}
            // Need to inspect the actual Gemini API response structure carefully.
            // Assuming the response *text* itself is the JSON we asked for:

             // Safely extract fields from the JSON response
             JsonNode feedbackNode = rootNode.path("feedback"); // Use path for safe access
             JsonNode resultNode = rootNode.path("result");
             JsonNode errorNode = rootNode.path("error");

             feedback.setcomments(feedbackNode.isMissingNode() ? "Evaluation complete (no specific feedback text)." : feedbackNode.asText());
             String resultText = resultNode.isMissingNode() ? null : resultNode.asText();
             feedback.setAccuracy(parseAccuracy(resultText)); // Use helper to parse accuracy
             feedback.setError(errorNode.isMissingNode() || errorNode.isNull() ? null : errorNode.asText());


        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "EvaluatorAgent: Failed to parse JSON response from Gemini for assignment " + assignmentId + ". Response was: " + jsonResponse, e);
            // Store the raw response or a generic error message in feedback
            feedback.setcomments("Evaluation completed, but result parsing failed.");
            feedback.setError("Failed to parse evaluation results. Raw Response: " + jsonResponse);
            feedback.setAccuracy(null);
           // Don't re-throw here, save the partial feedback object
        } catch (Exception e) { // Catch unexpected errors during parsing/processing
             LOGGER.log(Level.SEVERE, "EvaluatorAgent: Unexpected error processing Gemini response for assignment " + assignmentId + ". Response was: " + jsonResponse, e);
             feedback.setcomments("Evaluation completed, but an unexpected error occurred during result processing.");
             feedback.setError("Unexpected processing error: " + e.getMessage());
             feedback.setAccuracy(null);
        }

        // 6. Save the Feedback to the Database
        try {
            LOGGER.log(Level.INFO, "EvaluatorAgent: Saving feedback for assignment {0}, student {1}", new Object[]{assignmentId, studentId});
            dbClient.saveFeedback(feedback);
            LOGGER.log(Level.INFO, "EvaluatorAgent: Feedback saved with ID {0}", feedback.getId());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "EvaluatorAgent: Failed to save feedback for assignment " + assignmentId + ", student " + studentId, e);
            throw new RuntimeException("Evaluation complete but failed to save feedback", e); // Re-throw DB error
        }

        // 7. Return the raw JSON response from Gemini (as it contains all details)
        // Alternatively, return feedback.getId() or a status message.
        return jsonResponse;
    }
}