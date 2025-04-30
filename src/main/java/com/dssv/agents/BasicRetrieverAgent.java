package com.dssv.agents;

import com.dssv.logic.KeywordExtractor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

public class BasicRetrieverAgent implements RetrieverAgent {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    private static final String USER_AGENT        = "DSSV-RetrieverAgent/1.0";
    private static final int    TIMEOUT_MS        = 10_000;
    private static final int    MAX_HITS          = 3;
    private static final int    MAX_EXCERPT_LEN   = 150;
    private static final String GITHUB_TOKEN      = System.getenv("GITHUB_TOKEN");

    public BasicRetrieverAgent() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        this.mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String retrieve(String query) {
        if (query == null || query.isBlank()) {
            return "[Retriever: No query provided]";
        }

        StringBuilder results = new StringBuilder();

        // 1) Extract top 5 keywords
        List<String> keywords = KeywordExtractor.topKeywords(query, 5);
        if (keywords.isEmpty()) {
            // Fallback: split on whitespace
            keywords = Arrays.stream(query.split("\\s+"))
                            .limit(5)
                            .map(String::toLowerCase)
                            .collect(Collectors.toList());
        }

        // 2) Build focused GitHub search query
        String focused = String.join(" ", keywords) + " in:file language:python";

        String encodedQuery = URLEncoder.encode(focused, StandardCharsets.UTF_8);

        // --- 3. GitHub Code Search via REST API ---
        results.append("GitHub Code Context (API):\n");
        try {
            // Build and send search request
            String apiUrl = "https://api.github.com/search/code?q=" + encodedQuery;
            HttpRequest.Builder searchReq = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMillis(TIMEOUT_MS))
                .GET();
            if (GITHUB_TOKEN != null && !GITHUB_TOKEN.isBlank()) {
                searchReq.header("Authorization", "token " + GITHUB_TOKEN);
            }

            HttpResponse<String> resp = httpClient.send(
                searchReq.build(),
                HttpResponse.BodyHandlers.ofString()
            );

            if (resp.statusCode() != 200) {
                results.append("[GitHub API error: HTTP ").append(resp.statusCode()).append("]\n");
                return results.toString();
            }

            // Parse results
            CodeSearchResult searchResult = mapper.readValue(resp.body(), CodeSearchResult.class);
            if (searchResult.items == null || searchResult.items.isEmpty()) {
                results.append("No code results found.\n");
                return results.toString();
            }

            // Process top N hits
            int count = Math.min(MAX_HITS, searchResult.items.size());
            for (int i = 0; i < count; i++) {
                CodeItem item = searchResult.items.get(i);
                String repoFull  = item.repository.full_name;
                String filePath = item.path;          // e.g. "src/Main.java"
                String htmlUrl  = item.html_url;      // GitHub UI link

                String desc    = fetchRepoDescriptionOrReadme(repoFull);
                String excerpt = fetchFileContentExcerpt(repoFull, filePath);

                results.append(String.format(
                    "- [%s]\n" +
                    "    • File: %s\n" +
                    "    • URL: %s\n" +
                    "    • Description: %s\n" +
                    "    • Content Excerpt:\n```\n%s\n```\n\n",
                    repoFull, filePath, htmlUrl, desc, excerpt
                ));
            }


        } catch (IOException e) {
            results.append("[GitHub API IOException: ").append(e.getMessage()).append("]\n");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            results.append("[GitHub API Interrupted]\n");
        } catch (Exception e) {
            results.append("[GitHub API Exception: ").append(e.getMessage()).append("]\n");
        }

        return results.toString();
    }

    // --- Helper Methods ---

    /**
     * Return the repository description if present; otherwise fall back to README excerpt.
     */
    private String fetchRepoDescriptionOrReadme(String fullRepoName) {
        String desc = fetchRepoDescription(fullRepoName);
        if (desc != null && !desc.isBlank()) {
            return desc.trim();
        }
        String readme = fetchContentBase64(
            "https://api.github.com/repos/" + fullRepoName + "/readme"
        );
        return (readme != null)
            ? trim(readme)
            : "No description or README available.";
    }

    /**
     * Fetches the repository's `description` field from the repos API.
     */
    private String fetchRepoDescription(String fullRepoName) {
        try {
            String url = "https://api.github.com/repos/" + fullRepoName;
            HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofMillis(TIMEOUT_MS))
                .GET();
            if (GITHUB_TOKEN != null && !GITHUB_TOKEN.isBlank()) {
                req.header("Authorization", "token " + GITHUB_TOKEN);
            }
            HttpResponse<String> r = httpClient.send(
                req.build(),
                HttpResponse.BodyHandlers.ofString()
            );
            if (r.statusCode() != 200) {
                return null;
            }
            RepoInfo info = mapper.readValue(r.body(), RepoInfo.class);
            return info.description;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetches the file content via the GitHub Contents API and returns an excerpt.
     */
    private String fetchFileContentExcerpt(String fullRepoName, String path) {
        // Build the Contents API URL
        String contentsUrl = String.format(
            "https://api.github.com/repos/%s/contents/%s",
            fullRepoName,
            path
        );

        String full = fetchContentBase64(contentsUrl);
        return (full != null) ? trim(full) : "[Error fetching content]";
    }

    /**
     * Fetches Base64-encoded `content` from a GitHub Contents API endpoint,
     * decodes it, and returns the UTF-8 text.
     */
    private String fetchContentBase64(String apiUrl) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/vnd.github.v3+json")  // JSON with content field
                .timeout(Duration.ofMillis(TIMEOUT_MS))
                .GET();
            if (GITHUB_TOKEN != null && !GITHUB_TOKEN.isBlank()) {
                req.header("Authorization", "token " + GITHUB_TOKEN);
            }
            HttpResponse<String> r = httpClient.send(
                req.build(),
                HttpResponse.BodyHandlers.ofString()
            );
            if (r.statusCode() != 200) {
                return null;
            }

            // Deserialize JSON to get Base64 content
            BlobResponse blob = mapper.readValue(r.body(), BlobResponse.class);
            if (!"base64".equals(blob.encoding) || blob.content == null) {
                return null;
            }

            // Remove any whitespace (newlines) in the Base64 string
            String b64 = blob.content.replaceAll("\\s+", "");
            byte[] decoded = Base64.getDecoder().decode(b64);
            return new String(decoded, StandardCharsets.UTF_8);

        } catch (Exception e) {
            // Optionally log e.getMessage()
            return null;
        }
    }

    /**
     * Trim or truncate a text to MAX_EXCERPT_LEN characters.
     */
    private String trim(String text) {
        String t = text.replaceAll("\\r?\\n", " ").trim();
        if (t.length() <= MAX_EXCERPT_LEN) {
            return t;
        }
        return t.substring(0, MAX_EXCERPT_LEN) + "…";
    }

    // --- JSON mapping classes ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodeSearchResult {
        public int total_count;
        public boolean incomplete_results;
        public List<CodeItem> items;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodeItem {
        public String name;             // filename
        public String path;             // repository path
        public String html_url;         // web URL to file blob
        public String git_url;          // API URL for blob
        public Repository repository;   // nested repo info
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {
        public String full_name;        // owner/repo
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RepoInfo {
        public String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BlobResponse {
        public String content;          // Base64 content
        public String encoding;         // should be "base64"
    }
}
