package com.dssv.pojos;

public class Feedback {
    private String comments;
    // Could include specific suggestions, code snippets, etc.

    // Constructors, Getters, Setters
    public Feedback(String comments) { this.comments = comments; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
}