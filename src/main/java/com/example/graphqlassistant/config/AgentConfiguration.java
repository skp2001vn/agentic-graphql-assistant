package com.example.graphqlassistant.config;

import com.example.graphqlassistant.agent.AssistantOrchestrator;
import com.example.graphqlassistant.agent.AssistantRouter;
import com.example.graphqlassistant.agent.GenerationAgent;
import com.example.graphqlassistant.agent.LangChain4jAgentFactory;
import com.example.graphqlassistant.agent.SpecialistWorkflow;
import com.example.graphqlassistant.agent.TroubleshootingAgent;
import com.example.graphqlassistant.provider.AssistantAiProvider;
import com.example.graphqlassistant.schema.GraphqlSchemaContext;
import com.example.graphqlassistant.tools.GraphqlAssistantTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class AgentConfiguration {

  private static final double MINIMUM_ROUTING_CONFIDENCE = 0.7;

  @Bean
  GraphqlAssistantTools graphqlAssistantTools(GraphqlSchemaContext schemaContext) {
    return GraphqlAssistantTools.from(schemaContext);
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
      AssistantProperties properties) {
    return new AssistantOrchestrator(
        router,
        specialistWorkflow,
        graphqlAssistantTools,
        properties.getAi().getRequestTimeout(),
        MINIMUM_ROUTING_CONFIDENCE);
  }
}
