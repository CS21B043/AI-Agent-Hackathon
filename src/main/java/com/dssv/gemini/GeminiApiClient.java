package com.dssv.gemini; 

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.function.Consumer;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;   // For error handling

import com.dssv.gemini.GeminiModelInfo; 
import com.dssv.pojos.Message; 

// Consider using a proper JSON library (like Jackson, Gson, org.json) for robust payload creation and parsing.
// This example uses String manipulation for simplicity, similar to the original code, but it's less robust.
public class GeminiApiClient {

    private final String apiKey;
    private final HttpClient httpClient;

    public GeminiApiClient(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Gemini API Key cannot be null or empty.");
        }
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newHttpClient();
    }

    // --- Core Send Request Method ---

    private HttpResponse<String> sendRequest(String url, String jsonPayload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("API request failed with status code " + response.statusCode() + ": " + response.body());
        }
        return response;
    }

    // --- Text Generation ---

    public String generateText(String modelId, String prompt) throws IOException, InterruptedException {
        String url = GeminiModelInfo.getGenerateContentUrl(modelId, apiKey);
        String jsonPayload = String.format(
                "{\"contents\": [{\"parts\":[{\"text\": \"%s\"}]}]}",
                escapeJsonString(prompt) // Basic escaping
        );

        HttpResponse<String> response = sendRequest(url, jsonPayload);
        // WARNING: Very basic parsing. Use a JSON library for robustness.
        return extractTextFromResponse(response.body());
    }

    public String generateMultiTurnChat(String modelId, String payload)
        throws IOException, InterruptedException {
        String url = GeminiModelInfo.getGenerateContentUrl(modelId, apiKey);
        HttpResponse<String> response = sendRequest(url, payload);
        JsonNode root = new ObjectMapper().readTree(response.body());
        // Extract the first candidate’s text
        return root.path("candidates").get(0)
                .path("content")
                .path("parts").get(0)
                .path("text").asText();
    }

    public static void processStream(String responseBody, Consumer<String> onChunk, ObjectMapper mapper) {
        StringBuilder jsonBuffer = new StringBuilder();
        int braceDepth = 0; // To track nesting level of {}
        boolean inObject = false; // To track if we are currently inside a potential top-level object

        // Iterate character by character for precise object boundary detection
        for (int i = 0; i < responseBody.length(); i++) {
            char c = responseBody.charAt(i);

            // Start accumulating if we encounter the beginning of an object
            if (c == '{') {
                if (braceDepth == 0) { // Start of a new top-level object
                    jsonBuffer.setLength(0); // Clear buffer for the new object
                    inObject = true;
                }
                braceDepth++;
            }

            // Append the character if we are inside an object
            if (inObject) {
                jsonBuffer.append(c);
            }

            if (c == '}') {
                 if (braceDepth > 0) { // Avoid decrementing below zero if JSON is malformed
                     braceDepth--;
                 }
                 // Check if we've closed the top-level object
                if (braceDepth == 0 && inObject) {
                    inObject = false; // No longer actively inside this object
                    try {
                        String potentialJson = jsonBuffer.toString();
                        // System.out.println("Attempting to parse: " + potentialJson); // Debugging: show buffered object
                        JsonNode chunk = mapper.readTree(potentialJson);

                        // Navigate safely using path() which returns MissingNode if path doesn't exist
                        JsonNode textNode = chunk.path("candidates")
                                                .path(0)           // Get first candidate safely
                                                .path("content")
                                                .path("parts")
                                                .path(0)           // Get first part safely
                                                .path("text");

                        if (!textNode.isMissingNode()) { // Check if the text field was found
                            onChunk.accept(textNode.asText());
                        } else {
                            // Handle cases where the structure might differ slightly (e.g., error messages)
                            // Or maybe the last chunk has different info (like finishReason)
                             System.err.println("Warning: 'text' field not found in chunk: " + potentialJson.substring(0, Math.min(potentialJson.length(), 100)) + "...");
                        }

                    } catch (JsonProcessingException e) {
                        // Handle cases where the buffered content isn't valid JSON
                        System.err.println("Error processing JSON chunk: " + jsonBuffer.toString());
                        e.printStackTrace();
                        // Decide if you want to stop processing or try to recover
                    }
                    // Buffer is implicitly ready for the next object as it's cleared when '{' at depth 0 is found
                }
            }
            // Ignore characters outside of top-level objects (like the outer [], commas, whitespace)
        }

        if (braceDepth != 0) {
             System.err.println("Warning: Stream ended with unbalanced braces. Depth: " + braceDepth + ". Buffer: " + jsonBuffer.toString());
        }
    }

    public void streamMultiTurnChat(String modelId, String payload, Consumer<String> onChunk) throws IOException, InterruptedException {

        String url = GeminiModelInfo.getStreamingContentUrl(modelId, apiKey);
        HttpResponse<String> response = sendRequest(url, payload);
        ObjectMapper mapper = new ObjectMapper();
        String res = response.body();
        System.out.println("Response Body: " + res); // Debugging output
        processStream(res, onChunk, mapper);
    }


    public String generateTextWithConfig(String modelId, String prompt, Map<String, Object> generationConfig) throws IOException, InterruptedException {
        String url = GeminiModelInfo.getGenerateContentUrl(modelId, apiKey);
        // Basic JSON construction for generationConfig. Needs a proper library for complex objects.
        String configJson = generationConfig.entrySet().stream()
            .map(entry -> "\"" + entry.getKey() + "\":" + formatJsonValue(entry.getValue()))
            .collect(Collectors.joining(", ", "{", "}"));

        String jsonPayload = String.format(
                "{\"contents\": [{\"parts\":[{\"text\": \"%s\"}]}], \"generationConfig\": %s}",
                escapeJsonString(prompt),
                configJson
        );

        HttpResponse<String> response = sendRequest(url, jsonPayload);
        return extractTextFromResponse(response.body());
    }


    // --- Image Generation ---
    // Note: Uses a model assumed to support image generation output.
    // The original example used "gemini-2.0-flash-exp-image-generation".
    // Imagen 3 ("imagen-3.0-generate-002") is specifically for image generation.
    public byte[] generateImage(String modelId, String prompt) throws IOException, InterruptedException {
        String url = GeminiModelInfo.getGenerateContentUrl(modelId, apiKey);

        // This payload asks for TEXT and IMAGE modalities. Adjust if the model *only* generates images.
        // For a pure image generation model like Imagen 3, the payload might be simpler.
        // Let's stick to the structure similar to the original code for now.
         String jsonPayload = String.format(
             "{\"contents\": [{\"parts\":[{\"text\": \"%s\"}]}], \"generationConfig\":{\"responseModalities\":[\"TEXT\",\"IMAGE\"]}}",
             escapeJsonString(prompt)
         );

        // If using Imagen 3, the payload might look more like:
        // String jsonPayload = String.format("{\"prompt\": {\"text\": \"%s\"}}", escapeJsonString(prompt));
        // And the URL endpoint would likely be different (e.g., :generateImage instead of :generateContent)
        // --> The provided curl examples don't show Imagen 3's payload structure, so we adapt the multi-modal one.

        HttpResponse<String> response = sendRequest(url, jsonPayload);

        // WARNING: Very basic parsing. Use a JSON library for robustness.
        return extractImageDataFromResponse(response.body());
    }


    // --- Multimodal Input (Text + Inline Image Data for Understanding) ---
     public String generateContentWithInlineImage(String modelId, String textPrompt, String imageMimeType, byte[] imageData) throws IOException, InterruptedException {
        String url = GeminiModelInfo.getGenerateContentUrl(modelId, apiKey);
        String base64ImageData = Base64.getEncoder().encodeToString(imageData);

        String jsonPayload = String.format(
                "{\"contents\":[{\"parts\":[" +
                "{\"inline_data\": {\"mime_type\":\"%s\", \"data\": \"%s\"}}," +
                "{\"text\": \"%s\"}" +
                "]}]}",
                imageMimeType, base64ImageData, escapeJsonString(textPrompt)
        );

         HttpResponse<String> response = sendRequest(url, jsonPayload);
         return extractTextFromResponse(response.body());
     }

    // --- Multimodal Input (Text + Inline Document Data for Understanding) ---
    public String generateContentWithInlineDocument(String modelId, String textPrompt, Path documentPath) throws IOException, InterruptedException {
         String mimeType = Files.probeContentType(documentPath); // Basic MIME type detection
         if (mimeType == null) {
             mimeType = "application/octet-stream"; // Default fallback
             System.err.println("Warning: Could not determine MIME type for " + documentPath + ". Using default.");
         }
         byte[] documentBytes = Files.readAllBytes(documentPath);
         String base64DocumentData = Base64.getEncoder().encodeToString(documentBytes);

         String url = GeminiModelInfo.getGenerateContentUrl(modelId, apiKey);
         String jsonPayload = String.format(
                 "{\"contents\":[{\"parts\":[" +
                 "{\"inline_data\": {\"mime_type\":\"%s\", \"data\": \"%s\"}}," +
                 "{\"text\": \"%s\"}" +
                 "]}]}",
                 mimeType, base64DocumentData, escapeJsonString(textPrompt)
         );

         HttpResponse<String> response = sendRequest(url, jsonPayload);
         return extractTextFromResponse(response.body());
     }

    // --- Multimodal Input (Text + Document Data in Bytes for Understanding) ---
    public String generateContentWithDocumentBytes(String modelId, String textPrompt, String mimeType, byte[] documentBytes) throws IOException, InterruptedException {
         String base64DocumentData = Base64.getEncoder().encodeToString(documentBytes);

         String url = GeminiModelInfo.getGenerateContentUrl(modelId, apiKey);
         String jsonPayload = String.format(
                 "{\"contents\":[{\"parts\":[" +
                 "{\"inline_data\": {\"mime_type\":\"%s\", \"data\": \"%s\"}}," +
                 "{\"text\": \"%s\"}" +
                 "]}]}",
                 mimeType, base64DocumentData, escapeJsonString(textPrompt)
         );

         HttpResponse<String> response = sendRequest(url, jsonPayload);
         return extractTextFromResponse(response.body());
     }

    // --- Placeholder for File Upload (Needed for Audio/Video/Large Images) ---
    // This is complex, involving multiple API calls (start, upload, finalize)
    // Returning a dummy URI for now. Replace with actual implementation.
    private String uploadFile(Path filePath) throws IOException, InterruptedException {
         System.out.println("--- File Upload Simulation ---");
         String fileName = filePath.getFileName().toString();
         String mimeType = Files.probeContentType(filePath);
         if (mimeType == null) mimeType = "application/octet-stream";
         long numBytes = Files.size(filePath);
         System.out.printf("Simulating upload for: %s, Type: %s, Size: %d bytes%n", fileName, mimeType, numBytes);

         // 1. Call START upload endpoint (POST to GeminiModelInfo.getFileUploadUrl(apiKey))
         //    - Headers: X-Goog-Upload-Protocol: resumable, X-Goog-Upload-Command: start, ...
         //    - Body: {'file': {'display_name': '...'}}
         //    - Extract 'x-goog-upload-url' from response headers.
         String uploadUrl = "https://example.com/upload/dummy-url-for-" + fileName; // Placeholder
         System.out.println("Received simulated upload URL: " + uploadUrl);

         // 2. Call UPLOAD endpoint (POST/PUT to uploadUrl)
         //    - Headers: Content-Length, X-Goog-Upload-Offset: 0, X-Goog-Upload-Command: upload, finalize
         //    - Body: Raw file bytes from filePath
         //    - Get response body containing file metadata (name, uri, etc.)
         String fileUri = "gs://dummy-bucket/path/to/" + fileName; // Placeholder
         System.out.println("Simulated upload complete. File URI: " + fileUri);
         System.out.println("--- End File Upload Simulation ---");

         // Return the file URI needed for the generateContent call
         // In a real implementation, parse the JSON response from step 2
         return fileUri;
    }

    // --- Placeholder for Multimodal Input using Uploaded Files ---
    public String generateContentWithUploadedFile(String modelId, String textPrompt, Path filePath) throws IOException, InterruptedException {
         String fileUri = uploadFile(filePath); // Simulate or implement real upload
         String mimeType = Files.probeContentType(filePath);
          if (mimeType == null) mimeType = "application/octet-stream";

         String url = GeminiModelInfo.getGenerateContentUrl(modelId, apiKey);
         String jsonPayload = String.format(
                 "{\"contents\":[{\"parts\":[" +
                 "{\"text\": \"%s\"}," + // Order might matter, check API docs
                 "{\"file_data\":{\"mime_type\": \"%s\", \"file_uri\": \"%s\"}}" +
                 "]}]}",
                 escapeJsonString(textPrompt), mimeType, escapeJsonString(fileUri)
         );

         HttpResponse<String> response = sendRequest(url, jsonPayload);
         return extractTextFromResponse(response.body());
    }


    // --- Placeholder for Function Calling ---
    // Needs a way to represent the Tool structure (FunctionDeclarations) in Java and serialize to JSON
    public String generateWithFunctionCalling(String modelId, String prompt, String toolsJson) throws IOException, InterruptedException {
         String url = GeminiModelInfo.getGenerateContentUrl(modelId, apiKey);
         // NOTE: toolsJson needs to be a valid JSON string representing the "tools" array structure
         String jsonPayload = String.format(
                 "{\"contents\": [{\"role\": \"user\", \"parts\":[{\"text\": \"%s\"}]}], \"tools\": %s}",
                 escapeJsonString(prompt),
                 toolsJson // Pass the pre-formatted JSON string for tools
         );

         HttpResponse<String> response = sendRequest(url, jsonPayload);
         // Response will contain either text or a functionCall part - requires careful parsing
         // For simplicity, just returning the whole body for inspection
         System.out.println("Function Call Raw Response: " + response.body());
         // TODO: Add parsing logic to extract text or function call details
         return response.body();
    }

    // --- Placeholder for Code Execution ---
     public String generateWithCodeExecution(String modelId, String prompt) throws IOException, InterruptedException {
         String url = GeminiModelInfo.getGenerateContentUrl(modelId, apiKey);
         // Note the simple "code_execution": {} structure
         String jsonPayload = String.format(
             "{\"tools\": [{\"code_execution\": {}}], \"contents\": {\"parts\": {\"text\": \"%s\"}}}",
             escapeJsonString(prompt)
         );

         HttpResponse<String> response = sendRequest(url, jsonPayload);
         // Response will contain text and potentially executableCode parts.
         System.out.println("Code Execution Raw Response: " + response.body());
         // TODO: Add parsing logic to extract text and/or code results
         return response.body(); // Return raw response for now
     }


    // --- Utility Methods ---

    // Basic JSON string escaping (replace quotes and backslashes). Might need more robust escaping.
    private String escapeJsonString(String input) {
        return input.replace("\\", "\\\\").replace("\"", "\\\"");
    }

     // Basic JSON value formatting. Needs proper library for complex types/nesting.
    private String formatJsonValue(Object value) {
        if (value instanceof String) {
            return "\"" + escapeJsonString((String) value) + "\"";
        } else if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        } else {
            // Fallback for other types (e.g., lists, maps) - requires real JSON lib
             System.err.println("Warning: Unsupported type in generationConfig: " + value.getClass() + ". Using toString().");
            return "\"" + escapeJsonString(value.toString()) + "\"";
        }
    }


    // WARNING: Very basic parsing. Use a JSON library (Jackson, Gson, org.json) for robustness.
    private String extractTextFromResponse(String responseBody) throws IOException {
        // Simple search for the first occurrence of text content
        String marker = "\"text\": \"";
        int start = responseBody.indexOf(marker);
        if (start == -1) {
             // Maybe it's a function call or code execution response? Or error?
             System.err.println("Warning: Could not find 'text' field in response body:\n" + responseBody);
             return "[No text content found]"; // Or throw exception
        }
        start += marker.length();
        int end = responseBody.indexOf("\"", start);
        if (end == -1) {
            throw new IOException("Could not parse text content from response: " + responseBody);
        }
        // Handle basic JSON escape sequences like \n, \\, \"
        return responseBody.substring(start, end)
                 .replace("\\n", "\n")
                 .replace("\\\"", "\"")
                 .replace("\\\\", "\\");
    }

    // WARNING: Very basic parsing. Use a JSON library for robustness.
    private byte[] extractImageDataFromResponse(String responseBody) throws IOException {
        String marker = "\"data\": \""; // Assumes inline data with "data" field
        int start = responseBody.indexOf(marker);
        if (start < 0) {
            // Check if the response structure is different (e.g., maybe uses "uri" for generated images)
             System.err.println("Response Body for Image Gen: " + responseBody);
            throw new IOException("No 'data' field found in image generation response. Body: " + responseBody);
        }
        start += marker.length();
        int end = responseBody.indexOf('"', start);
         if (end < 0) {
             throw new IOException("Could not find end quote for image data. Body: " + responseBody);
         }
        String base64 = responseBody.substring(start, end);
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IOException("Failed to decode Base64 image data.", e);
        }
    }
}