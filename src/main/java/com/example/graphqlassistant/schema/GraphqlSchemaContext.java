package com.example.graphqlassistant.schema;

import graphql.schema.idl.TypeDefinitionRegistry;

public record GraphqlSchemaContext(
    String schemaText, TypeDefinitionRegistry typeDefinitionRegistry) {}
