package com.dssv.gemini; // CHANGE ME to your package

import java.io.IOException;
// Removed unused HTTP client imports from here
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths; // Use Paths for creating Path objects
import java.time.LocalDate;
import java.util.ArrayList;
// Removed unused Base64 import
import java.util.List;
import java.util.Map; // For generationConfig example
import java.util.UUID;

import com.dssv.gemini.GeminiApiClient; 
import com.dssv.gemini.GeminiModelInfo; // Assuming this is the correct import for your model info class
public class GeminiExamples {

    // New class demonstrating direct Gemini API calls via GeminiApiClient
    public static class Direct_Gemini_API_Calls {

        public static void main(String[] args) {
            String geminiApiKey = System.getenv("GEMINI_API_KEY");
            if (geminiApiKey == null || geminiApiKey.isBlank()) {
                System.err.println("Please set the GEMINI_API_KEY environment variable.");
                return;
            }

            GeminiApiClient client = new GeminiApiClient(geminiApiKey);
            String defaultTextModel = GeminiModelInfo.getModelId("gemini-2.0-flash"); // Or "gemini-1.5-flash" etc.
            // String defaultImageModel = GeminiModelInfo.getModelId("imagen-3"); // Use Imagen for generation
            String defaultImageModel = GeminiModelInfo.getModelId("gemini-2.0-flash-image-gen-exp"); // Or the experimental one

            try {
                // // --- Text Generation Example ---
                // System.out.println("\n--- Testing Text Generation ---");
                // String textPrompt = "Explain the concept of 'Infrastructure as Code' (IaC) in simple terms.";
                // String textResponse = client.generateText(defaultTextModel, textPrompt);
                // System.out.println("Model: " + defaultTextModel);
                // System.out.println("Prompt: " + textPrompt);
                // System.out.println("Response:\n" + textResponse);
                // System.out.println("------------------------------");

                //  // --- Text Generation with Config Example ---
                // System.out.println("\n--- Testing Text Generation with Config ---");
                // String controlledPrompt = "List two popular JavaScript frontend frameworks.";
                // Map<String, Object> config = Map.of(
                //     "temperature", 0.5,
                //     "maxOutputTokens", 50
                //     // "response_mime_type", "application/json" // Example if you expect JSON output
                // );
                // String controlledResponse = client.generateTextWithConfig(defaultTextModel, controlledPrompt, config);
                // System.out.println("Model: " + defaultTextModel);
                // System.out.println("Prompt: " + controlledPrompt);
                // System.out.println("Config: " + config);
                // System.out.println("Response:\n" + controlledResponse);
                // System.out.println("---------------------------------------");


                // // --- Image Generation Example ---
                // System.out.println("\n--- Testing Image Generation ---");
                // String imagePrompt = "A watercolor painting of a futuristic cityscape at sunset.";
                // // String imagePrompt = "Hi, can you create a 3d rendered image of three girls and a boy, all Indians of age 22, winning $5K in a hackathon and celebrating? Two of the girls are tall and slim, one is short with curly hair, and the boy is of medium height and fat";

                // byte[] imageBytes = client.generateImage(defaultImageModel, imagePrompt);
                // Path outputPath = Paths.get("gemini-generated-image.png");
                // Files.write(outputPath, imageBytes);
                // System.out.println("Model: " + defaultImageModel);
                // System.out.println("Prompt: " + imagePrompt);
                // System.out.println("Image saved to: " + outputPath.toAbsolutePath());
                // System.out.println("-------------------------------");

                //  // --- Image Understanding Example (Inline Data) ---
                //  System.out.println("\n--- Testing Image Understanding (Inline) ---");
                //  // Use the image we just generated (or provide path to another image)
                //  if (Files.exists(outputPath)) {
                //      byte[] inputImageBytes = Files.readAllBytes(outputPath);
                //      String imageUnderstandingPrompt = "Describe this image in detail.";
                //      // Use a model capable of vision input, like 1.5 Pro, 1.5 Flash, or 2.0 Flash
                //      String visionModel = GeminiModelInfo.getModelId("gemini-1.5-flash");
                //      String description = client.generateContentWithInlineImage(
                //          visionModel,
                //          imageUnderstandingPrompt,
                //          "image/png", // Adjust MIME type if using a different image
                //          inputImageBytes
                //      );
                //      System.out.println("Model: " + visionModel);
                //      System.out.println("Prompt: " + imageUnderstandingPrompt);
                //      System.out.println("Response:\n" + description);
                //  } else {
                //      System.out.println("Skipping image understanding test as generated image not found.");
                //  }
                //  System.out.println("-----------------------------------------");

                // // --- Document Understanding Example (Inline PDF) ---
                //  System.out.println("\n--- Testing Document Understanding (Inline PDF) ---");
                //  // Create a dummy PDF file for testing or provide a path to a real one
                //  Path pdfPath = Paths.get("CS527L.pdf");
                //  if (!Files.exists(pdfPath)) {
                //      // Create a simple text file and name it PDF for testing purposes
                //      // NOTE: The API might reject this if it validates PDF structure.
                //      // Use a real, small PDF for better testing.
                //       Files.writeString(pdfPath, "This is a simple test document content.");
                //       System.out.println("Created dummy PDF for testing: " + pdfPath.toAbsolutePath());
                //  }

                //  if (Files.exists(pdfPath)) {
                //      String docPrompt = "Create a question paper based on the syllabus provided.";
                //      String visionModel = GeminiModelInfo.getModelId("gemini-2.0-flash"); // Or 1.5 Pro

                //      try {
                //         String summary = client.generateContentWithInlineDocument(visionModel, docPrompt, pdfPath);
                //         System.out.println("Model: " + visionModel);
                //         System.out.println("Prompt: " + docPrompt);
                //         System.out.println("Document: " + pdfPath.getFileName());
                //         System.out.println("Response:\n" + summary);
                //      } catch (IOException e) {
                //           System.err.println("Failed Document Understanding: " + e.getMessage());
                //           // Check if the error is due to invalid PDF format if using the dummy file
                //      } finally {
                //          // Clean up the dummy file if created
                //          // if (pdfPath.getFileName().toString().equals("dummy_document.pdf")) Files.deleteIfExists(pdfPath);
                //      }
                //  } else {
                //      System.out.println("Skipping document understanding, PDF file not found at: " + pdfPath.toAbsolutePath());
                //  }
                //  System.out.println("------------------------------------------------");


                //  // --- Function Calling Example (Direct API) ---
                //  System.out.println("\n--- Testing Function Calling (Direct API) ---");
                //  String functionPrompt = "What's the weather like in Tokyo and Paris tomorrow?";
                //  // Define the tool specification as a JSON String (Matching the curl example structure)
                //  // Using a JSON library to build this string is highly recommended for complex tools.
                //  String toolsJson = """
                //  [
                //    {
                //      "functionDeclarations": [
                //        {
                //          "name": "get_current_weather",
                //          "description": "Get the current weather in a given location",
                //          "parameters": {
                //            "type": "OBJECT",
                //            "properties": {
                //              "location": {
                //                "type": "STRING",
                //                "description": "The city and state, e.g. San Francisco, CA"
                //              },
                //              "unit": {
                //                "type": "STRING",
                //                "enum": ["celsius", "fahrenheit"]
                //              }
                //            },
                //            "required": ["location"]
                //          }
                //        }
                //      ]
                //    }
                //  ]
                //  """;
                //  // Use a model that supports function calling well (e.g., 1.5 Flash, 2.0 Flash)
                //  String functionModel = GeminiModelInfo.getModelId("gemini-1.5-flash");
                //  String functionResponse = client.generateWithFunctionCalling(functionModel, functionPrompt, toolsJson);
                //  System.out.println("Model: " + functionModel);
                //  System.out.println("Prompt: " + functionPrompt);
                //  System.out.println("Tools Spec JSON:\n" + toolsJson);
                //  System.out.println("Raw API Response (may contain function calls or text):\n" + functionResponse);
                //  // TODO: Parse the response to check for "functionCall" parts and handle them.
                //  System.out.println("----------------------------------------------");


                //  // --- Code Execution Example ---
                //  System.out.println("\n--- Testing Code Execution ---");
                //  String codePrompt = "Calculate the factorial of 15 and tell me the result.";
                //  String codeModel = GeminiModelInfo.getModelId("gemini-2.0-flash"); // Check model support
                //  String codeResponse = client.generateWithCodeExecution(codeModel, codePrompt);
                //  System.out.println("Model: " + codeModel);
                //  System.out.println("Prompt: " + codePrompt);
                //  System.out.println("Raw API Response (may contain text and code execution results):\n" + codeResponse);
                //  // TODO: Parse the response to check for "executableCode" and "toolCodeOutput" parts.
                //  System.out.println("---------------------------");


            } catch (Exception e) {
                System.err.println("An error occurred during API call: " + e.getMessage());
                // Log the stack trace for debugging
                e.printStackTrace();
            }
        }
    }
}