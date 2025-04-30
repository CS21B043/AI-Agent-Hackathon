# DSSV-Agent: Personalized AI Tutor for Coding Education

**Hackathon Project Pitch**

**Team:** DSSV

## 1. The Problem: Scaling Quality Coding Education

Traditional coding education often struggles with providing **timely, personalized feedback** at scale. Students submit code, wait for manual grading, and receive generic feedback, if any. Assignments are often one-size-fits-all, failing to adapt to individual learning paces or address specific weaknesses. Teachers are burdened with repetitive grading tasks, limiting their time for direct student interaction and curriculum improvement.

## 2. Our Solution: DSSV-Agent

DSSV-Agent is an innovative **AI-powered learning loop** designed to revolutionize the coding assignment process. It acts as an intelligent assistant for both students and teachers, automating key tasks while ensuring educational quality and personalization.

**Core Loop:**

1.  **AI Generates Assignments:** Creates coding tasks based on topics, course materials (PDFs), or identifies areas needing improvement from past performance.
2.  **Teacher Reviews (HITL):** Teachers are notified and can optionally review, refine, or approve AI-generated assignments before release, ensuring quality and relevance.
3.  **Student Submits Code:** Students work on the assignment and submit their solution.
4.  **AI Evaluates & Gives Feedback:** The system runs the code against hidden test cases, providing immediate, detailed feedback on correctness, errors, and potential improvements.
5.  **Feedback Drives Personalization:** This feedback is stored and used by the AI to tailor future assignments, focusing on areas where the student needs more practice.
6.  **AI Teacher Chat:** Students can interact with an AI chat agent (representing the teacher) to ask questions and discuss the assignment, getting instant help.

## 3. Hackathon Scoring Alignment

### Innovation (20%)

* **Interesting Premise:** We move beyond simple AI chatbots or static code checkers. DSSV-Agent implements a **closed-loop feedback system** where AI-driven evaluation directly informs AI-driven personalization for subsequent tasks. This continuous adaptation is the core innovation.
* **Creative Technology Implementation:** We utilize a multi-agent architecture (Teacher, Evaluator, Retriever) in Java. We creatively employ Gemini's capabilities for diverse tasks: structured code execution/evaluation, personalized content generation informed by history, PDF analysis for context, and conversational AI for student support. The integration of Retrieval-Augmented Generation (RAG) via GitHub search adds another layer of context-awareness.
* **Engaging Demo:** A demonstration (video) would showcase the seamless flow from assignment generation (including teacher HITL), student submission, instant AI evaluation/feedback, and how that feedback influences the *next* assignment generated for that specific student, highlighting the personalization aspect. The chat interaction would also be shown.

### Impact (20%)

* **Would We Use It?**
    * **Students:** Yes! Get instant feedback, practice targeted areas, learn at their own pace, and have an always-available "TA" for questions.
    * **Teachers:** Yes! Automate grading, gain insights into student struggles, ensure assignment quality via HITL, and free up time for higher-level teaching activities.
* **Organizational Use:** Absolutely. Schools, universities, bootcamps, and online learning platforms could deploy DSSV-Agent to:
    * Scale quality coding instruction.
    * Provide consistent, objective feedback 24/7.
    * Improve student engagement and learning outcomes.
    * Reduce instructor workload significantly.
* **Value/Purpose:** The clear purpose is to make coding education **more effective, personalized, scalable, and engaging** by intelligently automating the assignment and feedback cycle.

### Usability (20%)

* **Real-World Scenario:** Directly addresses the universal challenge in CS education of providing timely, individualized feedback and support to large classes. It tackles the bottleneck of manual grading and the limitations of static assignments.
* **Practicality:** The solution automates laborious tasks (generation, basic evaluation) while maintaining pedagogical control. The API-based design allows integration into existing Learning Management Systems (LMS) or educational platforms.
* **Sophisticated Features (Human-in-the-Loop):** The system explicitly includes **Human-in-the-Loop (HITL)**. Teachers are notified upon assignment creation (`notifyTeacher`) and can use the `updateAssignment` functionality to modify AI suggestions based on their expertise or specific course needs. This builds trust and ensures the AI serves as an assistant, not a replacement.
* **Responsible AI Practices:**
    * **HITL:** As mentioned, HITL is central, preventing fully autonomous (and potentially incorrect or biased) assignment generation and ensuring teacher oversight.
    * **Objectivity:** Using automated tests for evaluation aims for objective assessment of code correctness, reducing potential human grading bias.
    * **Transparency (Area for Growth):** While logs exist, future versions could enhance transparency by explaining *why* an assignment was personalized in a specific way based on past feedback.
    * **Fairness:** The personalization aims to help students based on their *demonstrated* needs. HITL helps mitigate potential biases that could arise from the AI model itself. Careful prompt engineering and teacher review are key.

### Solution Quality & Alignment (Covered in Technical README)

* The project demonstrates substantial Java implementation, core AI integration, and addresses a complex problem with a sophisticated, modular design. (See Technical README for details).

## 4. Conclusion

DSSV-Agent offers a compelling vision for the future of coding education. By combining the power of AI for automation and personalization with essential human oversight, it creates a powerful tool to enhance learning effectiveness and scalability. It directly tackles real-world educational challenges with an innovative, practical, and impactful solution built robustly in Java.