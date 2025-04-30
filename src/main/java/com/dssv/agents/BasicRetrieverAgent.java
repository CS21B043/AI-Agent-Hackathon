package com.dssv.agents;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;               // still used if you want to add any Jsoup logic later
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

public class BasicRetrieverAgent implements RetrieverAgent {

    private final HttpClient httpClient;
    private static final String USER_AGENT = "DSSVTeacherAgent/1.0 (+https://example.com/bot)";
    private static final int TIMEOUT_MS = 10_000;
    // GitHub API token (optional—for higher rate limits/private repos)
    private static final String GITHUB_TOKEN = System.getenv("GITHUB_TOKEN");
    // How many top results to return
    private static final int MAX_HITS = 5;

    // JSON mapper for GitHub API responses
    private final ObjectMapper mapper;

    public BasicRetrieverAgent() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String retrieve(String query) {
        if (query == null || query.isBlank()) {
            return "[Retriever: No query provided]";
        }

        String ModifiedQuery = query + " Assignments";
        StringBuilder results = new StringBuilder();
        String encodedQuery = URLEncoder.encode(ModifiedQuery, StandardCharsets.UTF_8);

        // --- GitHub Code Search via REST API ---
        results.append("GitHub Code Context (API):\n");
        try {
            // 1) Search code endpoint
            String q = encodedQuery + "+in:file+language:python";
            String apiUrl = "https://api.github.com/search/code?q=" + q;
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

            // 2) Deserialize
            CodeSearchResult searchResult = mapper.readValue(resp.body(), CodeSearchResult.class);
            if (searchResult.items == null || searchResult.items.isEmpty()) {
                results.append("No code results found.\n");
                return results.toString();
            }

            // 3) Enrich top N with repo descriptions
            int count = Math.min(MAX_HITS, searchResult.items.size());
            for (int i = 0; i < count; i++) {
                CodeItem item = searchResult.items.get(i);
                String fullName = item.repository.full_name;    // e.g. "owner/repo"
                String fileUrl  = item.html_url;                // web URL to file
                String path     = item.path;                    // file path in repo

                // Fetch repo details for description
                String desc = fetchRepoDescription(fullName);

                results.append(String.format(
                    "- [%s] %s\n" +
                    "  • File Path: %s\n" +
                    "  • URL: %s\n" +
                    "  • Description: %s\n\n",
                    fullName,
                    item.name,
                    path,
                    fileUrl,
                    desc
                ));
            }

        } catch (Exception e) {
            results.append("[GitHub API exception: ").append(e.getMessage()).append("]\n");
        }

        return results.toString();
    }

    private String fetchRepoDescription(String fullRepoName) {
        try {
            String repoUrl = "https://api.github.com/repos/" + URLEncoder.encode(fullRepoName, StandardCharsets.UTF_8);
            HttpRequest.Builder repoReq = HttpRequest.newBuilder()
                    .uri(URI.create(repoUrl))
                    .header("User-Agent", USER_AGENT)
                    .timeout(Duration.ofMillis(TIMEOUT_MS))
                    .GET();
            if (GITHUB_TOKEN != null && !GITHUB_TOKEN.isBlank()) {
                repoReq.header("Authorization", "token " + GITHUB_TOKEN);
            }

            HttpResponse<String> r = httpClient.send(
                    repoReq.build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (r.statusCode() != 200) {
                return "N/A";
            }

            RepoInfo repo = mapper.readValue(r.body(), RepoInfo.class);
            return repo.description != null ? repo.description : "No description.";

        } catch (IOException | InterruptedException e) {
            return "Error fetching description.";
        }
    }

    // --- JSON mapping classes for GitHub API ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodeSearchResult {
        public int total_count;
        public boolean incomplete_results;
        public List<CodeItem> items;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CodeItem {
        public String name;            // filename
        public String path;            // path in repo
        public String html_url;        // web URL: https://github.com/.../blob/...
        public Repository repository;  // repo metadata
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Repository {
        public String full_name;       // e.g. "owner/repo"
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RepoInfo {
        public String description;
    }
}
