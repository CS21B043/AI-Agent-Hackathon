package com.dssv.resources;

import jakarta.inject.Inject; // Or use appropriate DI mechanism
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map; // For config map
import java.util.List; // For messages list
import java.nio.charset.StandardCharsets;

// Assume GeminiApiClient is your class interacting with the actual Gemini API
import com.dssv.gemini.GeminiApiClient;
import com.dssv.gemini.GeminiModelInfo; // If used for model IDs
import com.dssv.pojos.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;   // For error handling


@Path("/gemini/v1") // Base path for all Gemini related endpoints
public class GeminiApiResource {

    // --- Configuration ---
    // Ideally, inject these or load from config
    private final String defaultTextModel = GeminiModelInfo.getModelId("gemini-1.5-flash"); // Example
    private final String defaultImageModel = GeminiModelInfo.getModelId("imagen-3"); // Example
    private final String defaultVisionModel = GeminiModelInfo.getModelId("gemini-1.5-flash"); // Example
    private final String defaultCodeModel = GeminiModelInfo.getModelId("gemini-2.0-flash"); // Example
    private final String defaultFunctionModel = GeminiModelInfo.getModelId("gemini-1.5-flash"); // Example


    // Use Dependency Injection (like CDI or Spring) or instantiate manually
    // @Inject // Example using Jakarta EE CDI
    private GeminiApiClient client;

    public GeminiApiResource() {
        String geminiApiKey = System.getenv("GEMINI_API_KEY");
            if (geminiApiKey == null || geminiApiKey.isBlank()) {
                System.err.println("Please set the GEMINI_API_KEY environment variable.");
                return;
        }
        // Manual instantiation if not using DI
        this.client = new GeminiApiClient(geminiApiKey);
        System.out.println("GeminiApiResource initialized."); // Simple check
    }

    // --- Text Generation Endpoint ---
    @POST
    @Path("/generate/text")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response generateText(TextRequest request) {
        if (request == null || request.prompt == null || request.prompt.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"'prompt' is required.\"}").build();
        }
        try {
            System.out.println("Received text generation request for prompt: " + request.prompt);
            String textResponse = client.generateText(defaultTextModel, request.prompt);
            System.out.println("Generated response: " + textResponse.substring(0, Math.min(textResponse.length(), 100)) + "...");
            return Response.ok(new GeminiTextResponse(textResponse)).build();
        } catch (Exception e) {
            System.err.println("Error generating text: " + e.getMessage());
            e.printStackTrace(); // Log the full stack trace
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\": \"Failed to generate text: " + e.getMessage() + "\"}").build();
        }
    }

    @POST
    @Path("/chat")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response chat(ChatRequest request) {
        if (request == null || request.getMessages().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"'messages' must include at least one turn.\"}")
                        .build();
        }
        try {
            // 1. Build the contents array from the conversation history
            List<Map<String, Object>> contents = request.getMessages().stream()
                .map(msg -> Map.of(
                    "role", msg.getRole(),
                    "parts", List.of(Map.of("text", msg.getText()))
                ))
                .toList();

            // 2. Serialize payload
            String payload = new ObjectMapper()
                .writeValueAsString(Map.of("contents", contents));
            String reply = client.generateMultiTurnChat(defaultTextModel, payload);
            System.out.println("Generated response: " + reply.substring(0, Math.min(reply.length(), 100)) + "...");
            return Response.ok(new GeminiTextResponse(reply)).build();
            } catch (Exception e) {
            System.err.println("Error generating text: " + e.getMessage());
            e.printStackTrace(); // Log the full stack trace
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\": \"Failed to generate text: " + e.getMessage() + "\"}").build();
        }
    }

    @POST
    @Path("/chat/stream")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response streamChat(ChatRequest request) {
        if (request == null || request.getMessages().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                        .entity("{\"error\":\"'messages' must include at least one turn.\"}")
                        .build();
        }

        StreamingOutput stream = output -> {
            // Build same payload as above
            List<Map<String, Object>> contents = request.getMessages().stream()
                .map(msg -> Map.of(
                    "role", msg.getRole(),
                    "parts", List.of(Map.of("text", msg.getText()))
                ))
                .toList();
            String payload = new ObjectMapper()
                .writeValueAsString(Map.of("contents", contents));

            System.out.println("Streaming chat request payload: " + payload.substring(0, Math.min(payload.length(), 100)) + "...");
            try {
                // Use the client to stream the response
                client.streamMultiTurnChat(defaultTextModel, payload, chunk -> {
                    try{    
                        output.write(chunk.getBytes(StandardCharsets.UTF_8));
                        output.flush();
                    } catch (IOException e) {
                        System.err.println("Error writing chunk to output stream: " + e.getMessage());
                        e.printStackTrace(); // Log the full stack trace
                    }
                });
            } catch (Exception e) {
                System.err.println("Error during streaming: " + e.getMessage());
                e.printStackTrace(); // Log the full stack trace
                throw new RuntimeException("Streaming error: " + e.getMessage(), e);
            }
        };

        // Use chunked transfer encoding automatically via StreamingOutput :contentReference[oaicite:3]{index=3}.
        return Response.ok(stream).build();
    }


    // --- Text Generation with Config Endpoint ---
    @POST
    @Path("/generate/text/configured")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response generateTextWithConfig(ConfiguredTextRequest request) {
        if (request == null || request.prompt == null || request.prompt.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"'prompt' is required.\"}").build();
        }
        // Basic validation for config (can be more detailed)
        Map<String, Object> config = request.config != null ? request.config : Map.of();

        try {
             System.out.println("Received configured text generation request for prompt: " + request.prompt + " with config: " + config);
            String textResponse = client.generateTextWithConfig(defaultTextModel, request.prompt, config);
             System.out.println("Generated response: " + textResponse.substring(0, Math.min(textResponse.length(), 100)) + "...");
            return Response.ok(new GeminiTextResponse(textResponse)).build();
        } catch (Exception e) {
            System.err.println("Error generating configured text: " + e.getMessage());
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\": \"Failed to generate configured text: " + e.getMessage() + "\"}").build();
        }
    }

    // --- Image Generation Endpoint ---
    @POST
    @Path("/generate/image")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("image/png") // Or detect based on client capability/response
    public Response generateImage(TextRequest request) {
         if (request == null || request.prompt == null || request.prompt.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .type(MediaType.APPLICATION_JSON)
                           .entity("{\"error\": \"'prompt' is required.\"}").build();
        }
        try {
             System.out.println("Received image generation request for prompt: " + request.prompt);
            byte[] imageBytes = client.generateImage(defaultImageModel, request.prompt);
             System.out.println("Generated image of size: " + imageBytes.length + " bytes");
            return Response.ok(imageBytes).build();
        } catch (Exception e) {
            System.err.println("Error generating image: " + e.getMessage());
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .type(MediaType.APPLICATION_JSON)
                           .entity("{\"error\": \"Failed to generate image: " + e.getMessage() + "\"}").build();
        }
    }

    // --- Image Understanding Endpoint ---
    @POST
    @Path("/understand/image")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response understandImage(
            @FormDataParam("prompt") String prompt,
            @FormDataParam("image") InputStream imageInputStream,
            @FormDataParam("image") FormDataContentDisposition fileMetaData) {

        if (prompt == null || prompt.isEmpty()) {
             return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"'prompt' is required.\"}").build();
        }
        if (imageInputStream == null || fileMetaData == null) {
             return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"'image' file part is required.\"}").build();
        }

        try {
            System.out.println("Received image understanding request. Prompt: " + prompt + ", Filename: " + fileMetaData.getFileName());
            // Read image bytes from the input stream
            byte[] imageBytes = imageInputStream.readAllBytes();
            imageInputStream.close(); // Close the stream

            if (imageBytes.length == 0) {
                return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"Uploaded image is empty.\"}").build();
            }

            // Determine MIME type (simple approach, might need improvement)
            String mimeType = determineMimeType(fileMetaData.getFileName(), "image/png"); // Default to png if unsure
            System.out.println("Using MIME type: " + mimeType);


            String description = client.generateContentWithInlineImage(
                    defaultVisionModel,
                    prompt,
                    mimeType,
                    imageBytes
            );
            System.out.println("Generated description: " + description.substring(0, Math.min(description.length(), 100)) + "...");
            return Response.ok(new GeminiTextResponse(description)).build();

        } catch (IOException e) {
            System.err.println("Error reading uploaded image: " + e.getMessage());
             e.printStackTrace();
             return Response.status(Response.Status.BAD_REQUEST) // Could be server error too
                           .entity("{\"error\": \"Failed to read uploaded image: " + e.getMessage() + "\"}").build();
        } catch (Exception e) {
             System.err.println("Error understanding image: " + e.getMessage());
             e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\": \"Failed to understand image: " + e.getMessage() + "\"}").build();
        }
    }

     // --- Document Understanding Endpoint (PDF) ---
    @POST
    @Path("/understand/document")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response understandDocument(
            @FormDataParam("prompt") String prompt,
            @FormDataParam("document") byte[] docBytes,
            @FormDataParam("document") FormDataContentDisposition fileMetaData) {

         if (prompt == null || prompt.isEmpty()) {
             return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"'prompt' is required.\"}").build();
        }
        if (docBytes == null || fileMetaData == null) {
             return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"'document' file part is required.\"}").build();
        }
         // Basic check for PDF - could be more robust
        if (!fileMetaData.getFileName().toLowerCase().endsWith(".pdf")) {
             return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"Only PDF documents are supported.\"}").build();
        }

        try {
            System.out.println("Received document understanding request. Prompt: " + prompt + ", Filename: " + fileMetaData.getFileName());
            // Assuming the client method takes a Byte Stream
            String summary = client.generateContentWithDocumentBytes(defaultVisionModel, prompt, "application/pdf", docBytes);
            System.out.println("Generated summary: " + summary.substring(0, Math.min(summary.length(), 100)) + "...");

            return Response.ok(new GeminiTextResponse(summary)).build();

        } catch (IOException e) {
            System.err.println("Error processing uploaded document: " + e.getMessage());
             e.printStackTrace();
             return Response.status(Response.Status.BAD_REQUEST) // Could be server error too
                           .entity("{\"error\": \"Failed to read/process uploaded document: " + e.getMessage() + "\"}").build();
        } catch (Exception e) { // Catch potential errors from GeminiApiClient
             System.err.println("Error understanding document: " + e.getMessage());
             e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\": \"Failed to understand document: " + e.getMessage() + "\"}").build();
        } 
    }


    // --- Function Calling Endpoint ---
    @POST
    @Path("/execute/function-call")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON) // Output is likely JSON from Gemini
    public Response executeFunctionCall(FunctionCallRequest request) {
        if (request == null || request.prompt == null || request.prompt.isEmpty() || request.toolsJson == null || request.toolsJson.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"'prompt' and 'toolsJson' are required.\"}").build();
        }
        try {
            System.out.println("Received function calling request. Prompt: " + request.prompt);
            String functionResponse = client.generateWithFunctionCalling(defaultFunctionModel, request.prompt, request.toolsJson);
            System.out.println("Received raw function call response from Gemini.");
            // Return the raw JSON response from the Gemini API
            return Response.ok(new GeminiRawJsonResponse(functionResponse)).build();
             // Alternative: Parse functionResponse JSON into a Map and return that Map
             // ObjectMapper mapper = new ObjectMapper(); // Jackson
             // Map<String, Object> jsonResponse = mapper.readValue(functionResponse, Map.class);
             // return Response.ok(jsonResponse).build();

        } catch (Exception e) {
            System.err.println("Error during function calling: " + e.getMessage());
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\": \"Failed during function calling: " + e.getMessage() + "\"}").build();
        }
    }


    // --- Code Execution Endpoint ---
    @POST
    @Path("/execute/code")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON) // Output is likely JSON from Gemini
    public Response executeCode(TextRequest request) {
         if (request == null || request.prompt == null || request.prompt.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                           .entity("{\"error\": \"'prompt' is required.\"}").build();
        }
        try {
            System.out.println("Received code execution request. Prompt: " + request.prompt);
            String codeResponse = client.generateWithCodeExecution(defaultCodeModel, request.prompt);
            System.out.println("Received raw code execution response from Gemini.");
            // Return the raw JSON response from the Gemini API
             return Response.ok(new GeminiRawJsonResponse(codeResponse)).build();
             // Similar alternative parsing as in function calling if needed

        } catch (Exception e) {
             System.err.println("Error during code execution: " + e.getMessage());
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                           .entity("{\"error\": \"Failed during code execution: " + e.getMessage() + "\"}").build();
        }
    }


    // --- Helper Method ---
    private String determineMimeType(String fileName, String defaultType) {
        if (fileName == null || fileName.isEmpty()) {
            return defaultType;
        }
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".png")) return "image/png";
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
        if (lowerName.endsWith(".gif")) return "image/gif";
        if (lowerName.endsWith(".webp")) return "image/webp";
        if (lowerName.endsWith(".heic")) return "image/heic";
        if (lowerName.endsWith(".heif")) return "image/heif";
        // Add more types as needed
        return defaultType; // Fallback
    }
}
