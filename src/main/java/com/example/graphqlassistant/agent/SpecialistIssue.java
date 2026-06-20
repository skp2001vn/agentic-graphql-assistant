package com.example.graphqlassistant.agent;

/**
 * Model-produced diagnosis containing a GraphQL issue, explanation, and corrective suggestion.
 *
 * @param issue concise issue category
 * @param details explanation grounded in validation or schema evidence
 * @param suggestion actionable repair guidance
 */
public record SpecialistIssue(String issue, String details, String suggestion) {}
