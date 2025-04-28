// File: src/main/java/com/dssv/pojos/ChatRequest.java
package com.dssv.pojos;

import java.util.List;

public class ChatRequest {
    private List<ChatMessage> messages;

    public ChatRequest() {}

    public ChatRequest(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }
}
