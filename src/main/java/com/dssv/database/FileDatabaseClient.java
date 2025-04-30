package com.dssv.database; 

import com.dssv.pojos.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule; 

import java.io.File;
import java.io.IOException;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileDatabaseClient implements DatabaseClient {

    private static final Logger LOGGER = Logger.getLogger(FileDatabaseClient.class.getName());
    private final Path baseDir;
    private final Path assignmentsDir;
    private final Path conversationsDir;
    private final Path feedbacksDir; // << NEW >>: Directory for feedback files
    private final Path studentAssignmentIndexFile;
    private final Path studentFeedbackIndexFile; // << NEW >>: Index file for student->feedback mapping
    private final ObjectMapper objectMapper;

    // Caches and Locks
    private final Map<String, List<String>> studentAssignmentIndexCache;
    private final Map<String, List<String>> studentFeedbackIndexCache; // << NEW >>
    private final ReadWriteLock assignmentIndexLock = new ReentrantReadWriteLock();
    private final ReadWriteLock feedbackIndexLock = new ReentrantReadWriteLock(); // << NEW >>: Separate lock for feedback index


    public FileDatabaseClient(String baseDirectoryPath) throws IOException {
        this.baseDir = Paths.get(baseDirectoryPath);
        this.assignmentsDir = baseDir.resolve("assignments");
        this.conversationsDir = baseDir.resolve("conversations");
        this.feedbacksDir = baseDir.resolve("feedbacks"); // << NEW >>
        this.studentAssignmentIndexFile = baseDir.resolve("student_assignment_index.json");
        this.studentFeedbackIndexFile = baseDir.resolve("student_feedback_index.json"); // << NEW >>

        // Ensure directories exist
        Files.createDirectories(assignmentsDir);
        Files.createDirectories(conversationsDir);
        Files.createDirectories(feedbacksDir); // << NEW >>

        // Configure ObjectMapper
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule()) // << NEW >> Register module for LocalDateTime
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // Prefer ISO strings for dates

        // Initialize caches by loading from files
        this.studentAssignmentIndexCache = loadIndexCache(studentAssignmentIndexFile);
        this.studentFeedbackIndexCache = loadIndexCache(studentFeedbackIndexFile); // << NEW >>
        initializeIndexFilesIfNotExist(); // Ensure index files are created if they were missing
    }

    // Generic method to load an index cache file
    private Map<String, List<String>> loadIndexCache(Path indexFile) throws IOException {
        if (Files.exists(indexFile)) {
            try {
                // Ensure thread-safety on load by reading into a new map
                Map<String, List<String>> loadedMap = objectMapper.readValue(indexFile.toFile(),
                        new TypeReference<Map<String, List<String>>>() {});
                // Convert inner lists to thread-safe lists if needed, though reads/writes are locked anyway
                // For simplicity, using ConcurrentHashMap and standard ArrayLists inside should suffice with locks.
                return new ConcurrentHashMap<>(loadedMap);
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "DATABASE: Failed to load index cache file: " + indexFile + ". Returning empty cache.", e);
                // If file is corrupt, start with an empty cache
            }
        } else {
             LOGGER.log(Level.INFO, "DATABASE: Index cache file not found: {0}. Initializing empty cache.", indexFile);
        }
        return new ConcurrentHashMap<>(); // Return new empty cache if file doesn't exist or fails to load
    }

     // Generic method to save an index cache file atomically
     private void saveIndexCache(Path indexFile, Map<String, List<String>> cache) throws IOException {
         Path tempIndexFile = null;
         try {
             tempIndexFile = Files.createTempFile(baseDir, indexFile.getFileName().toString() + "_", ".json.tmp");
             objectMapper.writeValue(tempIndexFile.toFile(), cache); // Write the specific cache
             Files.move(tempIndexFile, indexFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
             LOGGER.log(Level.INFO, "DATABASE: Saved index cache to file: {0}", indexFile);
         } catch (IOException e) {
             LOGGER.log(Level.SEVERE, "DATABASE: Failed to save index cache to file: " + indexFile, e);
             // Clean up temp file if move failed or writing failed
             if (tempIndexFile != null && Files.exists(tempIndexFile)) {
                 try { Files.delete(tempIndexFile); } catch (IOException ignored) {}
             }
             throw e; // Re-throw the exception
         }
     }

      // Helper to ensure index files exist after loading caches
     private void initializeIndexFilesIfNotExist() {
        try {
             if (!Files.exists(studentAssignmentIndexFile)) {
                 saveIndexCache(studentAssignmentIndexFile, studentAssignmentIndexCache); // Save empty cache if needed
                 LOGGER.info("DATABASE: Created empty student assignment index file: " + studentAssignmentIndexFile);
             }
             if (!Files.exists(studentFeedbackIndexFile)) { // << NEW >>
                 saveIndexCache(studentFeedbackIndexFile, studentFeedbackIndexCache); // Save empty cache if needed
                 LOGGER.info("DATABASE: Created empty student feedback index file: " + studentFeedbackIndexFile);
             }
        } catch (IOException e) {
            // Log error but don't prevent startup if possible
             LOGGER.log(Level.SEVERE, "DATABASE: Failed to create initial index files if they were missing.", e);
        }
     }

    // --- Assignment Methods (Existing - Minor adjustments maybe needed for consistency) ---

    @Override
    public List<Assignment> fetchAssignmentsByStudentId(String studentId) {
        // ... (Keep existing implementation, using studentAssignmentIndexCache and assignmentIndexLock) ...
         if (studentId == null) {
             return Collections.emptyList();
         }
         List<String> assignmentIds;
         assignmentIndexLock.readLock().lock(); // Use assignment lock
         try {
             assignmentIds = studentAssignmentIndexCache.getOrDefault(studentId, Collections.emptyList());
         } finally {
             assignmentIndexLock.readLock().unlock();
         }

         return assignmentIds.stream()
                 .map(this::fetchAssignmentByIdInternal) // Read each file
                 .filter(Objects::nonNull)
                 .collect(Collectors.toList());
    }

     // Internal fetch without logging, used by fetchAssignmentsByStudentId
     private Assignment fetchAssignmentByIdInternal(String assignmentId) {
          // ... (Keep existing implementation) ...
          if (assignmentId == null) return null;
          Path assignmentFile = assignmentsDir.resolve(assignmentId + ".json");
          if (!Files.exists(assignmentFile)) {
              return null;
          }
          try {
              return objectMapper.readValue(assignmentFile.toFile(), Assignment.class);
          } catch (IOException e) {
               LOGGER.log(Level.WARNING, "DATABASE: Failed to read assignment file " + assignmentFile, e);
               return null; // Return null if file is corrupted or unreadable
          }
     }

    @Override
    public Assignment fetchAssignmentById(String assignmentId) {
        // ... (Keep existing implementation) ...
         Assignment assignment = fetchAssignmentByIdInternal(assignmentId);
         if (assignment != null) {
              LOGGER.log(Level.FINE, "DATABASE: Fetched assignment ID {0}", assignmentId);
         } else {
              LOGGER.log(Level.INFO, "DATABASE: Assignment ID {0} not found.", assignmentId);
         }
         return assignment;
    }

    @Override
    public void saveAssignment(Assignment assignment) throws IOException {
         // ... (Keep existing implementation, ensuring it uses assignmentIndexLock and saves studentAssignmentIndexCache) ...
         Objects.requireNonNull(assignment, "Assignment object cannot be null.");
         Objects.requireNonNull(assignment.getId(), "Assignment ID cannot be null.");
         Objects.requireNonNull(assignment.getStudentIds(), "Assignment student ID list cannot be null.");

         String assignmentId = assignment.getId();
         List<String> studentIdsInAssignment = assignment.getStudentIds();

         LOGGER.log(Level.INFO, "DATABASE: Preparing to save assignment ID: {0} for students: {1}",
                       new Object[]{assignmentId, studentIdsInAssignment});

         // Save the Assignment JSON file (atomic write)
         Path assignmentFile = assignmentsDir.resolve(assignmentId + ".json");
         Path tempAssignmentFile = null;
         try {
             tempAssignmentFile = Files.createTempFile(assignmentsDir, assignmentId + "_", ".json.tmp");
             objectMapper.writeValue(tempAssignmentFile.toFile(), assignment);
             Files.move(tempAssignmentFile, assignmentFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
             LOGGER.log(Level.FINE, "DATABASE: Saved assignment file: {0}", assignmentFile);

             // Update the Student Assignment Index Cache and File
             assignmentIndexLock.writeLock().lock(); // Use assignment lock
             boolean indexChanged = false;
             try {
                 // Add assignment to index for each student in the list
                 for (String studentId : studentIdsInAssignment) {
                     if (studentId == null || studentId.isBlank()) {
                         LOGGER.warning("DATABASE: Skipping null/blank student ID found in assignment " + assignmentId);
                         continue;
                     }
                     List<String> currentAssignments = studentAssignmentIndexCache.computeIfAbsent(studentId, k -> new ArrayList<>());
                     if (!currentAssignments.contains(assignmentId)) {
                         currentAssignments.add(assignmentId);
                         LOGGER.log(Level.FINE, "DATABASE: Added assignment {0} to index for student {1}", new Object[]{assignmentId, studentId});
                         indexChanged = true;
                     }
                 }

                 // TODO (Advanced): Implement logic to REMOVE assignment ID from students NOT in the list anymore.
                 // This requires fetching the old state or iterating through the whole index.

                 // Save the index file ONLY if changes were made
                 if (indexChanged) {
                     saveIndexCache(studentAssignmentIndexFile, studentAssignmentIndexCache); // Save assignment index
                 } else {
                      LOGGER.log(Level.FINE, "DATABASE: Student assignment index unchanged for assignment {0}.", assignmentId);
                 }
             } finally {
                 assignmentIndexLock.writeLock().unlock();
             }

         } catch (IOException e) {
             LOGGER.log(Level.SEVERE, "DATABASE SAVE ERROR: Failed during assignment save for ID " + assignmentId, e);
             if (tempAssignmentFile != null && Files.exists(tempAssignmentFile)) {
                 try { Files.delete(tempAssignmentFile); } catch (IOException ignored) {}
             }
             throw e;
         }
    }


    // --- Conversation History Methods (Existing) ---
    @Override
    public List<Message> fetchConversationHistory(String studentId, String assignmentId) throws IOException {
        // ... (Keep existing implementation) ...
        Objects.requireNonNull(studentId, "Student ID cannot be null");
        Objects.requireNonNull(assignmentId, "Assignment ID cannot be null");
        Path historyFile = getConversationHistoryPath(studentId, assignmentId);
        if (!Files.exists(historyFile)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(historyFile.toFile(), new TypeReference<List<Message>>() {});
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read or parse conversation history file: " + historyFile, e);
            return Collections.emptyList(); // Return empty list on error
        }
    }

    @Override
    public void saveConversationHistory(String studentId, String assignmentId, List<Message> messages) throws IOException {
        // ... (Keep existing implementation using atomic write) ...
        Objects.requireNonNull(studentId, "Student ID cannot be null");
        Objects.requireNonNull(assignmentId, "Assignment ID cannot be null");
        Objects.requireNonNull(messages, "Message list cannot be null");
        Path historyFile = getConversationHistoryPath(studentId, assignmentId);
        Path tempHistoryFile = null;
        try {
            Files.createDirectories(historyFile.getParent());
            tempHistoryFile = Files.createTempFile(conversationsDir, studentId + "_" + assignmentId + "_", ".json.tmp");
            objectMapper.writeValue(tempHistoryFile.toFile(), messages);
            Files.move(tempHistoryFile, historyFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.log(Level.FINE, "Saved conversation history to file: {0}", historyFile);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "DATABASE SAVE ERROR: Failed during conversation history save for student " + studentId + ", assignment " + assignmentId, e);
            if (tempHistoryFile != null && Files.exists(tempHistoryFile)) {
                try { Files.delete(tempHistoryFile); } catch (IOException ignored) {}
            }
            throw e;
        }
    }

    private Path getConversationHistoryPath(String studentId, String assignmentId) {
        // ... (Keep existing implementation) ...
         String sanitizedStudentId = studentId.replaceAll("[^a-zA-Z0-9_.-]", "_");
         String sanitizedAssignmentId = assignmentId.replaceAll("[^a-zA-Z0-9_.-]", "_");
         String filename = sanitizedStudentId + "_" + sanitizedAssignmentId + ".json";
         return conversationsDir.resolve(filename);
    }

    // --- Feedback Methods (NEW) ---

    /**
     * Saves a Feedback object to its own file and updates the student-feedback index.
     */
    @Override
    public void saveFeedback(Feedback feedback) throws IOException {
        Objects.requireNonNull(feedback, "Feedback object cannot be null.");
        Objects.requireNonNull(feedback.getId(), "Feedback ID cannot be null.");
        Objects.requireNonNull(feedback.getStudentId(), "Feedback student ID cannot be null.");
        // Assignment ID in feedback can be null/optional depending on use case, let's allow it for now.

        String feedbackId = feedback.getId();
        String studentId = feedback.getStudentId();

        LOGGER.log(Level.INFO, "DATABASE: Preparing to save feedback ID: {0} for student: {1}",
                      new Object[]{feedbackId, studentId});

        // 1. Save the Feedback JSON file (atomic write)
        Path feedbackFile = feedbacksDir.resolve(feedbackId + ".json");
        Path tempFeedbackFile = null;
        try {
            tempFeedbackFile = Files.createTempFile(feedbacksDir, feedbackId + "_", ".json.tmp");
            objectMapper.writeValue(tempFeedbackFile.toFile(), feedback);
            Files.move(tempFeedbackFile, feedbackFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            LOGGER.log(Level.FINE, "DATABASE: Saved feedback file: {0}", feedbackFile);

            // 2. Update the Student Feedback Index Cache and File
            feedbackIndexLock.writeLock().lock(); // Use feedback lock
            boolean indexChanged = false;
            try {
                // Add the feedback ID to the index for the student
                List<String> currentFeedbacks = studentFeedbackIndexCache.computeIfAbsent(studentId, k -> new ArrayList<>());
                if (!currentFeedbacks.contains(feedbackId)) {
                    currentFeedbacks.add(feedbackId);
                    LOGGER.log(Level.FINE, "DATABASE: Added feedback {0} to index for student {1}", new Object[]{feedbackId, studentId});
                    indexChanged = true;
                } else {
                    // If feedback is being updated, the index entry usually doesn't need changing
                    // unless the studentId itself changed, which shouldn't happen for an existing feedback ID.
                     LOGGER.log(Level.FINEST, "DATABASE: Feedback {0} already indexed for student {1}.", new Object[]{feedbackId, studentId});
                }


                // Save the index file ONLY if changes were made
                if (indexChanged) {
                    saveIndexCache(studentFeedbackIndexFile, studentFeedbackIndexCache); // Save feedback index
                } else {
                     LOGGER.log(Level.FINE, "DATABASE: Student feedback index unchanged for feedback {0}.", feedbackId);
                }
            } finally {
                feedbackIndexLock.writeLock().unlock();
            }

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "DATABASE SAVE ERROR: Failed during feedback save for ID " + feedbackId, e);
            // Clean up temp file if move failed
            if (tempFeedbackFile != null && Files.exists(tempFeedbackFile)) {
                try { Files.delete(tempFeedbackFile); } catch (IOException ignored) {}
            }
            throw e; // Re-throw
        }
    }

     /**
      * Fetches a specific Feedback object by its ID from its file.
      * Returns null if not found or if there's an error reading the file.
      */
     private Feedback fetchFeedbackByIdInternal(String feedbackId) {
         if (feedbackId == null || feedbackId.isBlank()) {
             return null;
         }
         Path feedbackFile = feedbacksDir.resolve(feedbackId + ".json");
         if (!Files.exists(feedbackFile)) {
             return null;
         }
         try {
             // Assuming Feedback class is correctly set up for Jackson deserialization
             return objectMapper.readValue(feedbackFile.toFile(), Feedback.class);
         } catch (IOException e) {
             LOGGER.log(Level.WARNING, "DATABASE: Failed to read feedback file " + feedbackFile, e);
             return null; // Return null if file is corrupted or unreadable
         }
     }

    /**
     * Public method to fetch a Feedback object by its ID. Includes logging.
     */
    @Override
    public Feedback fetchFeedbackById(String feedbackId) {
        Feedback feedback = fetchFeedbackByIdInternal(feedbackId);
        if (feedback != null) {
             LOGGER.log(Level.FINE, "DATABASE: Fetched feedback ID {0}", feedbackId);
        } else {
             LOGGER.log(Level.INFO, "DATABASE: Feedback ID {0} not found.", feedbackId);
        }
        return feedback;
    }

    /**
     * Fetches all Feedback objects associated with a given student ID using the index.
     * Returns an empty list if the student has no feedback or an error occurs.
     */
    @Override
    public List<Feedback> fetchFeedbacksByStudentId(String studentId) throws IOException {
        if (studentId == null || studentId.isBlank()) {
            return Collections.emptyList();
        }

        List<String> feedbackIds;
        feedbackIndexLock.readLock().lock(); // Use feedback lock for reading the cache
        try {
            // Get IDs from the in-memory feedback cache
            feedbackIds = studentFeedbackIndexCache.getOrDefault(studentId, Collections.emptyList());
        } finally {
            feedbackIndexLock.readLock().unlock();
        }

        if (feedbackIds.isEmpty()) {
             LOGGER.log(Level.FINE, "DATABASE: No feedback indexed for student ID {0}", studentId);
            return Collections.emptyList();
        }

        LOGGER.log(Level.INFO, "DATABASE: Fetching {0} feedback entries for student ID {1}", new Object[]{feedbackIds.size(), studentId});

        // Fetch the actual Feedback objects from their individual files
        List<Feedback> feedbacks = feedbackIds.stream()
                .map(this::fetchFeedbackByIdInternal) // Read each feedback file
                .filter(Objects::nonNull) // Filter out any that failed to load
                .collect(Collectors.toList());

         // Optional: Sort feedback by timestamp if needed (most recent first?)
         feedbacks.sort(Comparator.comparing(Feedback::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())));


         return feedbacks;
    }

    // Optional: Implement fetchFeedbacksByAssignmentId if needed.
    // This would likely require iterating through all feedback files or creating
    // a separate assignment->feedback index, similar to the student index.
    // public List<Feedback> fetchFeedbacksByAssignmentId(String assignmentId) throws IOException {
    //     // Implementation would scan files or use an assignment-feedback index
    //     throw new UnsupportedOperationException("Fetching feedback by assignment ID not yet implemented efficiently.");
    // }
}