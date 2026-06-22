package com.example.graphqlassistant.api.model;

/**
 * Minimal readiness payload returned by the health endpoint.
 *
 * @param status application readiness state
 */
public record HealthResponse(String status) {}
