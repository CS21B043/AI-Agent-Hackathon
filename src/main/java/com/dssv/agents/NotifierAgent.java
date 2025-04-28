package com.dssv.agents;
interface NotifierAgent {
    void notifyTeacher(Assignment assignment) throws Exception;
    void notifyStudent(String studentId, Assignment assignment) throws Exception;
}