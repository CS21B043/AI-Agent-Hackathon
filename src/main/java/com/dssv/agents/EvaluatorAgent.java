package com.dssv.agents;

public interface EvaluatorAgent {
    String evaluate(String studentId, String assignmentId, String answer) throws Exception;
    String evaluate(String studentId, String assignmentId, String answer, String feedback) throws Exception;
}