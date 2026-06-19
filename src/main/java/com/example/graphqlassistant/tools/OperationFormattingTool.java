package com.example.graphqlassistant.tools;

import graphql.language.AstPrinter;
import graphql.parser.Parser;

final class OperationFormattingTool {

  OperationFormattingResult format(FormatOperationInput input) {
    return new OperationFormattingResult(
        AstPrinter.printAst(Parser.parse(input.operation())).strip());
  }
}
