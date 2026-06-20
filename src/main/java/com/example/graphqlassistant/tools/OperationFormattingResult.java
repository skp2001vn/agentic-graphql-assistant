package com.example.graphqlassistant.tools;

/**
 * Canonical GraphQL AST formatting result returned by the formatting tool.
 *
 * @param formattedOperation pretty-printed GraphQL operation
 */
public record OperationFormattingResult(String formattedOperation) {}
