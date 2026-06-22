package com.example.graphqlassistant.evals;

import com.example.graphqlassistant.agent.AssistantOrchestrator;
import com.example.graphqlassistant.agent.langchain4j.LangChain4jAgentFactory;
import com.example.graphqlassistant.assistant.AssistantService;
import com.example.graphqlassistant.provider.AssistantAiProvider;
import com.example.graphqlassistant.schema.GraphqlOperationProcessor;
import com.example.graphqlassistant.schema.GraphqlSchemaContext;
import com.example.graphqlassistant.tools.GraphqlAssistantTools;
import java.time.Duration;

final class EvalHarness {

  private EvalHarness() {
    throw new AssertionError("No instances");
  }

  static AssistantService createService(
      AssistantAiProvider provider, GraphqlSchemaContext schemaContext, Duration timeout) {
    GraphqlAssistantTools tools = GraphqlAssistantTools.from(schemaContext);
    var generationAgent = LangChain4jAgentFactory.createGenerationAgent(provider, tools);
    var troubleshootingAgent = LangChain4jAgentFactory.createTroubleshootingAgent(provider, tools);
    var workflow =
        LangChain4jAgentFactory.createSpecialistWorkflow(generationAgent, troubleshootingAgent);
    AssistantOrchestrator orchestrator =
        new AssistantOrchestrator(
            LangChain4jAgentFactory.createRouter(provider), workflow, tools, timeout, 0.7);
    return new AssistantService(orchestrator, new GraphqlOperationProcessor(schemaContext));
  }
}
