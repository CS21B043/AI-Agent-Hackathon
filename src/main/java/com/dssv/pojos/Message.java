package com.dssv.pojos;
import java.util.List;
import java.util.Map;
public class Message {
    private String role;   // "user" or "model"
    private String text;

    // Constructors, getters, setters omitted for brevity

    public Map<String, Object> toContentMap() {
        return Map.of(
            "role", role,
            "parts", List.of(Map.of("text", text))
        );
    }

    public Message(String role, String text) {
        this.role = role;
        this.text = text;
    }
    public String getRole() { return role; }
    public String getText() { return text; }
}
