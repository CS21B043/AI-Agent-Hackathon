// File: src/main/java/com/dssv/pojos/ChatMessage.java
package com.dssv.pojos;

public class ChatMessage {
    private String role;  // "user" or "model"
    private String text;  // the utterance

    public ChatMessage() {}

    public ChatMessage(String role, String text) {
        this.role = role;
        this.text = text;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
