package com.dssv.agents; // Or dedicated package e.g., com.dssv.retriever

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class BasicRetrieverAgent implements RetrieverAgent {

    private final HttpClient httpClient;

    public BasicRetrieverAgent() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)) // Short timeout
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String retrieve(String query) throws Exception {
        if (query == null || query.isBlank()) {
            return "[Retriever: No query provided]";
        }

        StringBuilder results = new StringBuilder();
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        // 1. DuckDuckGo Instant Answer API (Simple Web Search)
        try {
            // format=json, no_html=1 (remove HTML tags), skip_disambig=1 (skip disambiguation pages)
            String ddgUrl = "https://api.duckduckgo.com/?q=" + encodedQuery + "&format=json&no_html=1&skip_disambig=1&pretty=1";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ddgUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                     // DuckDuckGo API doesn't strictly need a User-Agent, but it's good practice
                    .header("User-Agent", "DSSVTeacherAgent/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                // VERY basic parsing - assumes structure like {"AbstractText" : "...", "RelatedTopics": [...]}
                // Use a JSON library (like Jackson/Gson) in production!
                String abstractText = extractSimpleJsonValue(responseBody, "AbstractText");
                //String heading = extractSimpleJsonValue(responseBody, "Heading"); // Could also grab heading

                 results.append("Web Context (from DuckDuckGo):\n");
                 if (abstractText != null && !abstractText.isBlank()) {
                     results.append("- Summary: ").append(abstractText).append("\n");
                 } else {
                     results.append("- No direct summary found.\n");
                 }
                 // Could try parsing RelatedTopics for more links/text if Abstract is empty

            } else {
                 results.append("[Retriever: DuckDuckGo query failed with status ")
                        .append(response.statusCode()).append("]\n");
            }

        } catch (Exception e) {
             System.err.println("Retriever Agent: Error calling DuckDuckGo API: " + e.getMessage());
             results.append("[Retriever: Error during web search: ").append(e.getMessage()).append("]\n");
             // Don't re-throw, just log and continue to GitHub part for partial results
        }


        // 2. GitHub Search Simulation
        try {
            String githubUrl = "https://github.com/search?q=" + encodedQuery + "+language%3APython&type=code"; // Sample: Add Python language filter
             System.out.println("Retriever: Simulating GitHub code search: " + githubUrl);
             results.append("\nSimulated GitHub Context:\n")
                    .append("- Potential relevant code snippets or repositories might exist on GitHub.\n")
                    .append("- Search URL: ").append(githubUrl).append("\n");
             // In a real system: Use GitHub API (requires token) to search code/repos
        } catch (Exception e) {
             // Should not happen with just URL construction, but for safety:
             System.err.println("Retriever Agent: Error constructing GitHub URL: " + e.getMessage());
             results.append("[Retriever: Error during GitHub simulation]\n");
        }


        return results.toString();
    }

     // Extremely basic JSON extractor - ONLY for simple key: "value" pairs. Use a real parser!
     private String extractSimpleJsonValue(String json, String key) {
         String searchKey = "\"" + key + "\": \"";
         int start = json.indexOf(searchKey);
         if (start == -1) return null; // Key not found or not string value start
         start += searchKey.length();
         int end = json.indexOf("\"", start); // Find closing quote
         if (end == -1) return null; // Malformed
         // Basic unescaping for this simple case
         return json.substring(start, end)
                    .replace("\\\"", "\"") // Unescape quotes
                    .replace("\\\\", "\\") // Unescape backslashes
                    .replace("\\n", "\n"); // Unescape newlines
     }
}