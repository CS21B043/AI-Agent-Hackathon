package com.dssv.services;

import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseBroadcaster;
import jakarta.ws.rs.sse.SseEventSink;
import jakarta.ws.rs.sse.OutboundSseEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;

/**
 * NotificationService manages Server-Sent Events (SSE) connections per user
 * and broadcasts named JSON events to them.
 */
@Singleton
public class NotificationService {

    @Inject
    private Sse sse;  // SSE context for building events and broadcasters

    // One broadcaster per userId
    private final Map<String, SseBroadcaster> broadcasters = new ConcurrentHashMap<>();

    /**
     * Registers a new SSE connection (SseEventSink) for the given userId.
     * Creates a dedicated SseBroadcaster for the user (if not exist),
     * sets up onClose/onError callbacks, and registers the sink.
     *
     * @param userId    Unique identifier of the user.
     * @param eventSink The SSE sink representing the open connection.
     */
    public void register(String userId, SseEventSink eventSink) {
        if (userId == null || eventSink == null) {
            return;
        }

        // Create or retrieve the broadcaster for this user
        SseBroadcaster broadcaster = broadcasters.computeIfAbsent(userId, id -> {
            SseBroadcaster bc = sse.newBroadcaster();

            // When a client disconnects (sink closed), log and clean up
            bc.onClose(sink -> {
                System.out.println("SSE Service: Unregistered sink for user: " + id + " (closed)");
            });

            // On error sending to any sink, log the error
            bc.onError((sink, error) -> {
                System.err.println("SSE Service: Error on sink for user: " + id +
                                   " - " + error.getMessage());
            });

            return bc;
        });

        // Register this connection with its broadcaster
        broadcaster.register(eventSink);
        System.out.println("SSE Service: Registered sink for user: " + userId);
    }

    /**
     * Sends a named JSON event to all SSE connections for a specific user.
     *
     * @param userId    The target userId.
     * @param payload   The object to serialize as JSON.
     * @param eventName The event name (e.g., "teacher_notification").
     */
    public void sendNotification(String userId, String payload, String eventName) {
        SseBroadcaster broadcaster = broadcasters.get(userId);
        if (broadcaster == null) {
            System.out.println("SSE Service: No broadcaster found for user: " + userId);
            return;
        }

        // Build the SSE event
        OutboundSseEvent event = sse.newEventBuilder()
                                    .name(eventName)
                                    .mediaType(MediaType.APPLICATION_JSON_TYPE)
                                    .data(payload.getClass(), payload)
                                    .build();

        // Broadcast asynchronously to all registered sinks
        CompletionStage<?> stage = broadcaster.broadcast(event);
        stage.whenComplete((__, err) -> {
            if (err != null) {
                System.err.println("SSE Service: Failed to send '" + eventName +
                                   "' to user " + userId + ": " + err.getMessage());
            }
        });
    }
}
