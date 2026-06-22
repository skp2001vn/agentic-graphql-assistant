package com.example.graphqlassistant.tools;

import com.example.graphqlassistant.tools.model.ArgumentSummary;
import com.example.graphqlassistant.tools.model.FieldSummary;
import com.example.graphqlassistant.tools.model.InspectSchemaInput;
import com.example.graphqlassistant.tools.model.SchemaInspectionResult;
import com.example.graphqlassistant.tools.model.TypeSummary;
import graphql.language.AstPrinter;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.InterfaceTypeDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.OperationTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class SchemaInspectionTool {

  private final TypeDefinitionRegistry registry;

  SchemaInspectionTool(TypeDefinitionRegistry registry) {
    this.registry = registry;
  }

  SchemaInspectionResult inspect(InspectSchemaInput input) {
    Set<String> names = new LinkedHashSet<>(rootTypeNames());
    names.addAll(input.typeNames());

    List<TypeSummary> types = new ArrayList<>();
    for (String name : names) {
      TypeDefinition<?> type = registry.getTypeOrNull(name);
      if (type != null) {
        types.add(summarize(type));
      }
    }
    return new SchemaInspectionResult(types);
  }

  private List<String> rootTypeNames() {
    return registry
        .schemaDefinition()
        .map(
            definition ->
                definition.getOperationTypeDefinitions().stream()
                    .map(OperationTypeDefinition::getTypeName)
                    .map(typeName -> typeName.getName())
                    .toList())
        .orElseGet(
            () ->
                List.of("Query", "Mutation", "Subscription").stream()
                    .filter(registry::hasType)
                    .toList());
  }

  private TypeSummary summarize(TypeDefinition<?> type) {
    List<FieldSummary> fields = fields(type);
    String kind = type.getClass().getSimpleName().replace("TypeDefinition", "").toUpperCase();
    return new TypeSummary(type.getName(), kind, fields);
  }

  private List<FieldSummary> fields(TypeDefinition<?> type) {
    if (type instanceof ObjectTypeDefinition objectType) {
      return objectType.getFieldDefinitions().stream().map(this::summarizeField).toList();
    }
    if (type instanceof InterfaceTypeDefinition interfaceType) {
      return interfaceType.getFieldDefinitions().stream().map(this::summarizeField).toList();
    }
    if (type instanceof InputObjectTypeDefinition inputObjectType) {
      return inputObjectType.getInputValueDefinitions().stream()
          .map(
              field ->
                  new FieldSummary(
                      field.getName(), AstPrinter.printAstCompact(field.getType()), List.of()))
          .toList();
    }
    return List.of();
  }

  private FieldSummary summarizeField(FieldDefinition field) {
    return new FieldSummary(
        field.getName(),
        AstPrinter.printAstCompact(field.getType()),
        field.getInputValueDefinitions().stream().map(this::summarizeArgument).toList());
  }

  private ArgumentSummary summarizeArgument(InputValueDefinition argument) {
    return new ArgumentSummary(argument.getName(), AstPrinter.printAstCompact(argument.getType()));
  }
}
