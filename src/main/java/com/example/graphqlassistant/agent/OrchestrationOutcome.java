package com.example.graphqlassistant.agent;

public sealed interface OrchestrationOutcome permits SpecialistOutcome, ClarificationOutcome {}
