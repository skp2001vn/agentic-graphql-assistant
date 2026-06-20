package com.example.graphqlassistant.schema;

import graphql.schema.idl.TypeDefinitionRegistry;

/**
 * Immutable schema-grounding context shared by agents, tools, and deterministic validators.
 *
 * @param schemaText original GraphQL SDL supplied to AI observability
 * @param typeDefinitionRegistry parsed schema registry used for inspection and validation
 */
public record GraphqlSchemaContext(
    String schemaText, TypeDefinitionRegistry typeDefinitionRegistry) {}
