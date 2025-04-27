package com.dssv.pojos;
// Generic JSON response for raw API output (like function calls/code exec)
public class GeminiRawJsonResponse {
    public String rawResponse; // Or potentially parse into a Map<String, Object>
    public GeminiRawJsonResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }
    // Default constructor
    public GeminiRawJsonResponse() {}
}