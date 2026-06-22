package com.example.graphqlassistant.tools;

/**
 * Compact GraphQL argument metadata returned to an AI specialist for schema grounding.
 *
 * @param name schema argument name
 * @param type exact GraphQL input type expression
 */
public record ArgumentSummary(String name, String type) {}
