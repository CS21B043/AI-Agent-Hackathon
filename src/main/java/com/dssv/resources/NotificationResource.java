// package com.dssv.resources; 

// import com.dssv.services.NotificationService; 

// import jakarta.inject.Inject; // Or framework equivalent
// import jakarta.ws.rs.GET;
// import jakarta.ws.rs.Path;
// import jakarta.ws.rs.PathParam;
// import jakarta.ws.rs.Produces;
// import jakarta.ws.rs.core.Context;
// import jakarta.ws.rs.core.MediaType;
// import jakarta.ws.rs.sse.Sse;
// import jakarta.ws.rs.sse.SseEventSink;

// @Path("/api/notifications")
// public class NotificationResource {

//     @Inject // Inject the Singleton NotificationService
//     private NotificationService notificationService;

//     @Inject // Inject Sse context
//     private Sse sse;

//     @GET
//     @Path("/subscribe/{userId}")
//     @Produces(MediaType.SERVER_SENT_EVENTS) // Crucial: Set the media type
//     public void subscribe(@PathParam("userId") String userId,
//                           @Context SseEventSink eventSink) { // Inject the sink
//         if (userId == null || userId.isBlank() || eventSink == null) {
//             System.err.println("SSE Resource: Invalid subscription request (userId or sink missing).");
//             // Consider closing the sink immediately if invalid
//              if(eventSink != null) eventSink.close();
//             return;
//         }
//         System.out.println("SSE Resource: Client connecting for user ID: " + userId);
//         // Register this connection with the central service
//         notificationService.register(userId, eventSink);
//         // The connection stays open until the client disconnects or an error occurs
//         // The NotificationService handles sending actual events later
//     }
// }

package com.dssv.resources;

import com.dssv.services.NotificationService;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.SseEventSink;
import jakarta.ws.rs.core.Context;

@Path("/notifications")
public class NotificationResource {

    @Inject
    private NotificationService notificationService;

    /**
     * Client calls this endpoint to start receiving SSE events.
     * E.g. new EventSource("/api/notifications/subscribe/john_doe").
     */
    @GET
    @Path("/subscribe/{userId}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void subscribe(
            @PathParam("userId") String userId,
            @Context SseEventSink eventSink) {

        // Register this client’s sink so future notifications will be pushed.
        notificationService.register(userId, eventSink);
    }
}
