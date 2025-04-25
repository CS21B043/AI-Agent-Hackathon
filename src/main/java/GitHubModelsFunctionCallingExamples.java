package com.example;

import dev.langchain4j.agent.tool.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.github.GitHubModelsChatModel;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.github.GitHubModelsChatModelName.GPT_4_O_MINI;

public class GitHubModelsFunctionCallingExamples {

    public static class Weather_From_Manual_Configuration {

        public static void main(String[] args) throws Exception {
            // Existing Weather example code...
            GitHubModelsChatModel model = GitHubModelsChatModel.builder()
                    .gitHubToken(System.getenv("GITHUB_TOKEN"))
                    .modelName(GPT_4_O_MINI)
                    .logRequestsAndResponses(true)
                    .build();

            // Step 1
            WeatherTools weatherTools = new WeatherTools();
            List<ToolSpecification> toolSpecifications = ToolSpecifications.toolSpecificationsFrom(weatherTools);
            List<ChatMessage> chatMessages = new ArrayList<>();
            chatMessages.add(userMessage("What will the weather be like in London tomorrow?"));

            ChatRequest request = ChatRequest.builder()
                    .messages(chatMessages)
                    .toolSpecifications(toolSpecifications)
                    .build();

            // Step 2
            AiMessage aiMessage = model.chat(request).aiMessage();
            chatMessages.add(aiMessage);

            // Step 3
            for (ToolExecutionRequest toolExecutionRequest : aiMessage.toolExecutionRequests()) {
                ToolExecutor executor = new DefaultToolExecutor(weatherTools, toolExecutionRequest);
                String result = executor.execute(toolExecutionRequest, UUID.randomUUID().toString());
                chatMessages.add(ToolExecutionResultMessage.from(toolExecutionRequest, result));
            }

            // Step 4
            AiMessage finalResponse = model.chat(chatMessages).aiMessage();
            System.out.println(finalResponse.text());

            // New: Test image generation via Google Gemini API
            String prompt = "Hi, can you create a 3d rendered image of three girls and a boy, all Indians of age 22, winning $5K in a hackathon and celebrating? Two of the girls are tall and slim, one is short with curly hair, and the boy is of medium height and fat";
            String apiKey = System.getenv("GEMINI_API_KEY");
            if (apiKey == null) {
                System.err.println("Please set GEMINI_API_KEY in your environment.");
                return;
            }
            byte[] imageBytes = generateImage(prompt, apiKey);
            Path outputPath = Path.of("gemini-image.png");
            Files.write(outputPath, imageBytes);
            System.out.println("Image saved to " + outputPath.toAbsolutePath());
        }

        /**
         * Calls Google Gemini image generation API and returns raw image bytes.
         */
        public static byte[] generateImage(String prompt, String apiKey) throws IOException, InterruptedException {
            HttpClient client = HttpClient.newHttpClient();
            String url = String.format(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp-image-generation:generateContent?key=%s",
                    apiKey);

            String jsonPayload = "{\n" +
                    "  \"contents\": [{\n" +
                    "    \"parts\": [\n" +
                    "      {\"text\": \"" + prompt + "\"}\n" +
                    "    ]\n" +
                    "  }],\n" +
                    "  \"generationConfig\":{\"responseModalities\":[\"TEXT\",\"IMAGE\"]}\n" +
                    "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            // Extract base64 data from JSON (simple parse)
            String body = response.body();
            String marker = "\"data\": \"";
            int start = body.indexOf(marker);
            if (start < 0) throw new IOException("No data field in response");
            start += marker.length();
            int end = body.indexOf('"', start);
            String base64 = body.substring(start, end);
            return Base64.getDecoder().decode(base64);
        }
    }

    public static class WeatherTools {

        @Tool("Returns the weather forecast for tomorrow for a given city")
        String getWeather(@P("The city for which the weather forecast should be returned") String city) {
            return "The weather tomorrow in " + city + " is 25°C";
        }

        @Tool("Returns the date for tomorrow")
        LocalDate getTomorrow() {
            return LocalDate.now().plusDays(1);
        }

        @Tool("Transforms Celsius degrees into Fahrenheit")
        double celsiusToFahrenheit(@P("The celsius degree to be transformed into fahrenheit") double celsius) {
            return (celsius * 1.8) + 32;
        }

        String iAmNotATool() {
            return "I am not a method annotated with @Tool";
        }
    }
}