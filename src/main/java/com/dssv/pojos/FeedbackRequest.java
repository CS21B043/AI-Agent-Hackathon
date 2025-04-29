package com.dssv.pojos;

/**
 * Request payload for teacher feedback submissions.
 */
public class FeedbackRequest {
    private String comments;

    // No-arg constructor
    public FeedbackRequest() { }

    // All-args constructor
    public FeedbackRequest(String comments) {
        this.comments = comments;
    }

    // Getter
    public String getComments() {
        return comments;
    }

    // Setter
    public void setComments(String comments) {
        this.comments = comments;
    }
}
