package com.example.graphqlassistant.config;

import com.example.graphqlassistant.agent.AssistantOrchestrator;
import com.example.graphqlassistant.agent.AssistantRouter;
import com.example.graphqlassistant.agent.GenerationAgent;
import com.example.graphqlassistant.agent.LangChain4jAgentFactory;
import com.example.graphqlassistant.agent.SpecialistWorkflow;
import com.example.graphqlassistant.agent.TroubleshootingAgent;
import com.example.graphqlassistant.assistant.AssistantService;
import com.example.graphqlassistant.logging.AssistantRequestLogger;
import com.example.graphqlassistant.provider.AssistantAiProvider;
import com.example.graphqlassistant.schema.GraphqlOperationProcessor;
import com.example.graphqlassistant.schema.GraphqlOperationValidator;
import com.example.graphqlassistant.schema.GraphqlSchemaContext;
import com.example.graphqlassistant.tools.GraphqlAssistantTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composes the assistant's router, specialist agents, deterministic tools, and application service.
 *
 * <p>The bean graph forms a bounded multi-agent architecture: intent classification precedes a
 * conditional specialist workflow, tools ground the LLM in the configured schema, and a
 * deterministic processor validates all model output before publication.
 */
@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

  private static final double MINIMUM_ROUTING_CONFIDENCE = 0.7;

  @Bean
  GraphqlAssistantTools graphqlAssistantTools(
      GraphqlSchemaContext schemaContext,
      GraphqlOperationValidator operationValidator,
      AssistantRequestLogger requestLogger) {
    return GraphqlAssistantTools.from(schemaContext, operationValidator, requestLogger);
  }

  @Bean
  AssistantRouter assistantRouter(AssistantAiProvider provider) {
    return LangChain4jAgentFactory.createRouter(provider);
  }

  @Bean
  GenerationAgent generationAgent(
      AssistantAiProvider provider, GraphqlAssistantTools graphqlAssistantTools) {
    return LangChain4jAgentFactory.createGenerationAgent(provider, graphqlAssistantTools);
  }

  @Bean
  TroubleshootingAgent troubleshootingAgent(
      AssistantAiProvider provider, GraphqlAssistantTools graphqlAssistantTools) {
    return LangChain4jAgentFactory.createTroubleshootingAgent(provider, graphqlAssistantTools);
  }

  @Bean
  SpecialistWorkflow specialistWorkflow(
      GenerationAgent generationAgent, TroubleshootingAgent troubleshootingAgent) {
    return LangChain4jAgentFactory.createSpecialistWorkflow(generationAgent, troubleshootingAgent);
  }

  @Bean
  AssistantOrchestrator assistantOrchestrator(
      AssistantRouter router,
      SpecialistWorkflow specialistWorkflow,
      GraphqlAssistantTools graphqlAssistantTools,
      AssistantProperties properties,
      AssistantRequestLogger requestLogger) {
    return new AssistantOrchestrator(
        router,
        specialistWorkflow,
        graphqlAssistantTools,
        properties.getAi().getRequestTimeout(),
        MINIMUM_ROUTING_CONFIDENCE,
        requestLogger);
  }

  @Bean
  GraphqlOperationValidator graphqlOperationValidator(GraphqlSchemaContext schemaContext) {
    return new GraphqlOperationValidator(schemaContext);
  }

  @Bean
  GraphqlOperationProcessor graphqlOperationProcessor(
      GraphqlOperationValidator operationValidator) {
    return new GraphqlOperationProcessor(operationValidator);
  }

  @Bean
  AssistantService assistantService(
      AssistantOrchestrator orchestrator,
      GraphqlOperationProcessor operationProcessor,
      AssistantRequestLogger requestLogger,
      AssistantAiProvider provider,
      GraphqlSchemaContext schemaContext) {
    return new AssistantService(
        orchestrator, operationProcessor, requestLogger, provider, schemaContext);
  }
}
