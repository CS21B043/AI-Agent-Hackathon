package com.dssv.agents;

import com.dssv.pojos.Assignment;

public interface NotifierAgent {
    void notifyTeacher(Assignment assignment) throws Exception;
    void notifyStudent(String studentId, Assignment assignment) throws Exception;
}