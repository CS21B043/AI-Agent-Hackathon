package com.dssv; // CHANGE ME to your package

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.media.multipart.MultiPartFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.jackson.JacksonFeature; // Correct Jackson feature import

import com.dssv.resources.*;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    // Base URI the Grizzly HTTP server will listen on
    public static final String BASE_URI = "http://localhost:8080/"; // You can change the port

    // Starts Grizzly HTTP server exposing JAX-RS resources defined in this application.
    public static HttpServer startServer() {
        // Create a resource configuration that scans for JAX-RS resources and providers
        // in the com.dssv.resources package (or register classes explicitly)
        final ResourceConfig rc = new ResourceConfig()
            // Register resource classes directly (more explicit)
            .register(GeminiApiResource.class)
            .register(TeacherAgentResource.class)
            .register(NotificationResource.class)
             // Register features
            .register(JacksonFeature.class)    // Enable Jackson JSON processing
            .register(MultiPartFeature.class); // Enable multipart form data processing

            // Alternatively, scan packages (less explicit):
            // .packages("com.dssv.resources"); // CHANGE ME

        // Create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        System.out.println("Creating Grizzly server...");
        return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
    }

    public static void main(String[] args) throws IOException {
        // Configure Java Util Logging (JUL) which Grizzly uses
        Logger.getLogger("").setLevel(Level.INFO); // Set root logger level
        Logger.getLogger("org.glassfish.grizzly").setLevel(Level.WARNING); // Reduce Grizzly noise if needed

        System.out.println("Starting Gemini API server...");
        final HttpServer server = startServer();
        System.out.println(String.format("Jersey app started with endpoints available at "
                + "%s\nHit Ctrl-C to stop it...", BASE_URI));

        // Keep the server running until shutdown (e.g., Ctrl-C)
        try {
            // A simple way to keep the main thread alive
           Thread.currentThread().join();
           // Or use: System.in.read(); // Waits for Enter key press
        } catch (InterruptedException e) {
             System.out.println("Server interrupted.");
             Thread.currentThread().interrupt(); // Restore interrupt status
        } finally {
             System.out.println("Stopping server...");
             server.shutdownNow(); // Graceful shutdown can also be used: server.shutdown().get();
             System.out.println("Server stopped.");
        }
    }
}