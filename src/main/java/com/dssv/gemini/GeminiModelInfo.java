// src/main/java/com/example/GeminiModelInfo.java
package com.dssv.gemini; // CHANGE ME to your package

import java.util.Map;
import java.util.Optional;

public class GeminiModelInfo {

    // Map model aliases/names to their actual API IDs
    private static final Map<String, String> MODEL_IDS = Map.ofEntries(
            Map.entry("gemini-2.5-flash-preview", "gemini-2.5-flash-preview-04-17"),
            Map.entry("gemini-2.5-pro-preview", "gemini-2.5-pro-preview-03-25"),
            Map.entry("gemini-2.0-flash", "gemini-2.0-flash"),
            Map.entry("gemini-2.0-flash-image-gen-exp", "gemini-2.0-flash-exp-image-generation"), // Example from original code
            Map.entry("gemini-2.0-flash-lite", "gemini-2.0-flash-lite"),
            Map.entry("gemini-1.5-flash", "gemini-1.5-flash"),
            Map.entry("gemini-1.5-flash-8b", "gemini-1.5-flash-8b"),
            Map.entry("gemini-1.5-pro", "gemini-1.5-pro"),
            Map.entry("imagen-3", "imagen-3.0-generate-002"), // Based on curl example hint
            Map.entry("veo-2", "veo-2.0-generate-001") // Based on curl example hint
            // Add other models as needed
    );

    // Base URL for the generateContent endpoint
    private static final String BASE_GENERATE_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    // Base URL for file uploads (Needed for image/audio/video understanding via file_data)
    private static final String BASE_UPLOAD_URL = "https://generativelanguage.googleapis.com/upload/v1beta/files?key=%s";

    private static final String BASE_STREAM_URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:streamGenerateContent?key=%s";
    

    public static String getModelId(String modelName) {
        return Optional.ofNullable(MODEL_IDS.get(modelName))
                .orElseThrow(() -> new IllegalArgumentException("Unknown model name: " + modelName));
    }

    public static String getGenerateContentUrl(String modelId, String apiKey) {
        // Validate if the provided modelId is one of the known values (optional, but good practice)
        if (!MODEL_IDS.containsValue(modelId)) {
            System.err.println("Warning: Using an unknown or potentially unsupported modelId: " + modelId);
        }
        return String.format(BASE_GENERATE_URL, modelId, apiKey);
    }

    public static String getStreamingContentUrl(String modelId, String apiKey) {
        // Validate if the provided modelId is one of the known values (optional, but good practice)
        if (!MODEL_IDS.containsValue(modelId)) {
            System.err.println("Warning: Using an unknown or potentially unsupported modelId: " + modelId);
        }
        return String.format(BASE_STREAM_URL, modelId, apiKey);
    }

     public static String getFileUploadUrl(String apiKey) {
        return String.format(BASE_UPLOAD_URL, apiKey);
    }

    // You could add more metadata here, like supported input/output types per model if needed.
}