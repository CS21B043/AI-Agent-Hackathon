package com.dssv.filters;

import java.io.IOException;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Filter that adds CORS headers to each response
 */
@Provider
public class CorsFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        // Allow access from any origin
        responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");
        
        // For security in production, use specific origin instead of wildcard:
        // responseContext.getHeaders().add("Access-Control-Allow-Origin", "http://localhost:3000");
        
        // Allow specific HTTP methods
        responseContext.getHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        
        // Allow specific headers
        responseContext.getHeaders().add("Access-Control-Allow-Headers", 
            "Origin, Content-Type, Accept, Authorization, X-Requested-With");
        
        // Allow cookies and other credentials
        responseContext.getHeaders().add("Access-Control-Allow-Credentials", "true");
        
        // Cache preflight response for 86400 seconds (24 hours)
        responseContext.getHeaders().add("Access-Control-Max-Age", "86400");
    }
}