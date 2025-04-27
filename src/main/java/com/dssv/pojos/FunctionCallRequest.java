package com.dssv.pojos;
// For function calling input
public class FunctionCallRequest {
    public String prompt;
    public String toolsJson; // Assuming tools spec is passed as a JSON string
    // Default constructor
    public FunctionCallRequest() {}
}