package com.dssv.pojos;

import java.util.List;

public class ChatRequest {
    private List<Message> messages;

    public ChatRequest() {}

    public ChatRequest(List<Message> messages) {
        this.messages = messages;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }
}
