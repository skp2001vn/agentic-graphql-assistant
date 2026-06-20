package com.example.graphqlassistant.agent;

/** Closed result hierarchy for the router-specialist AI orchestration pipeline. */
public sealed interface OrchestrationOutcome permits SpecialistOutcome, ClarificationOutcome {}
