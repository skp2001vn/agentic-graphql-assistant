package com.example.graphqlassistant.api;

import static com.example.graphqlassistant.config.OpenApiConfiguration.AGENT_EXECUTION_ERROR;
import static com.example.graphqlassistant.config.OpenApiConfiguration.AI_PROVIDER_ERROR;
import static com.example.graphqlassistant.config.OpenApiConfiguration.CLARIFICATION_REQUIRED;
import static com.example.graphqlassistant.config.OpenApiConfiguration.GENERATE_REQUEST;
import static com.example.graphqlassistant.config.OpenApiConfiguration.GENERATE_RESPONSE;
import static com.example.graphqlassistant.config.OpenApiConfiguration.INTERNAL_ERROR;
import static com.example.graphqlassistant.config.OpenApiConfiguration.INVALID_AI_RESPONSE;
import static com.example.graphqlassistant.config.OpenApiConfiguration.INVALID_REQUEST;
import static com.example.graphqlassistant.config.OpenApiConfiguration.REQUEST_TOO_LARGE;
import static com.example.graphqlassistant.config.OpenApiConfiguration.TROUBLESHOOT_RESPONSE;
import static com.example.graphqlassistant.config.OpenApiConfiguration.UNSUPPORTED_MEDIA_TYPE;

import com.example.graphqlassistant.api.model.ApiError;
import com.example.graphqlassistant.api.model.AssistantResponse;
import com.example.graphqlassistant.api.model.GenerateResponse;
import com.example.graphqlassistant.api.model.TroubleshootResponse;
import com.example.graphqlassistant.assistant.AssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the natural-language GraphQL assistant through a single bounded HTTP endpoint.
 *
 * <p>The controller treats prompts as untrusted input, enforces UTF-8 and payload-size boundaries,
 * and delegates all AI routing, tool use, and schema grounding to the application service.
 */
@RestController
public class AssistantController {

  static final int MAX_REQUEST_BYTES = 100 * 1024;

  private final AssistantService assistantService;

  /**
   * Creates the HTTP adapter for the assistant use case.
   *
   * @param assistantService application service that owns AI orchestration and normalization
   */
  public AssistantController(AssistantService assistantService) {
    this.assistantService = assistantService;
  }

  /**
   * Generates or troubleshoots a GraphQL operation from a validated text prompt.
   *
   * @param request servlet request containing a bounded UTF-8 {@code text/plain} prompt
   * @return normalized generation or troubleshooting response
   */
  @PostMapping(
      path = "/assistant",
      consumes = MediaType.TEXT_PLAIN_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Generate or troubleshoot a GraphQL operation",
      description =
          "Routes a natural-language prompt to a bounded generation or troubleshooting agent.",
      requestBody =
          @RequestBody(
              required = true,
              description = "A nonblank UTF-8 prompt no larger than 100 KB.",
              content =
                  @Content(
                      mediaType = MediaType.TEXT_PLAIN_VALUE,
                      schema = @Schema(type = "string"),
                      examples = @ExampleObject(name = "generate", value = GENERATE_REQUEST))))
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Generated or corrected GraphQL operation",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(oneOf = {GenerateResponse.class, TroubleshootResponse.class}),
                examples = {
                  @ExampleObject(name = "generate", value = GENERATE_RESPONSE),
                  @ExampleObject(name = "troubleshoot", value = TROUBLESHOOT_RESPONSE)
                })),
    @ApiResponse(
        responseCode = "400",
        description = "Empty or malformed request",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class),
                examples = @ExampleObject(name = "invalidRequest", value = INVALID_REQUEST))),
    @ApiResponse(
        responseCode = "413",
        description = "Request body exceeds 100 KB",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class),
                examples = @ExampleObject(name = "requestTooLarge", value = REQUEST_TOO_LARGE))),
    @ApiResponse(
        responseCode = "415",
        description = "Content type is not UTF-8 text/plain",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class),
                examples =
                    @ExampleObject(name = "unsupportedMediaType", value = UNSUPPORTED_MEDIA_TYPE))),
    @ApiResponse(
        responseCode = "422",
        description = "Prompt is ambiguous or insufficient",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class),
                examples =
                    @ExampleObject(
                        name = "clarificationRequired",
                        value = CLARIFICATION_REQUIRED))),
    @ApiResponse(
        responseCode = "500",
        description = "Unexpected internal failure",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class),
                examples = @ExampleObject(name = "internalError", value = INTERNAL_ERROR))),
    @ApiResponse(
        responseCode = "502",
        description = "AI provider, model response, or bounded agent execution failure",
        content =
            @Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = @Schema(implementation = ApiError.class),
                examples = {
                  @ExampleObject(name = "aiProviderError", value = AI_PROVIDER_ERROR),
                  @ExampleObject(name = "invalidAiResponse", value = INVALID_AI_RESPONSE),
                  @ExampleObject(name = "agentExecutionError", value = AGENT_EXECUTION_ERROR)
                }))
  })
  public AssistantResponse assist(HttpServletRequest request) {
    return assistantService.assist(readPrompt(request));
  }

  private String readPrompt(HttpServletRequest request) {
    MediaType contentType = MediaType.parseMediaType(request.getContentType());
    if (contentType.getCharset() != null
        && !StandardCharsets.UTF_8.equals(contentType.getCharset())) {
      throw new ApiException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "UNSUPPORTED_MEDIA_TYPE",
          "Content-Type must be text/plain with UTF-8 encoding.");
    }

    byte[] body;
    try {
      body = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
    } catch (IOException exception) {
      throw invalidRequest();
    }
    if (body.length > MAX_REQUEST_BYTES) {
      throw new ApiException(
          HttpStatus.CONTENT_TOO_LARGE,
          "REQUEST_TOO_LARGE",
          "Request body must not exceed 100 KB.");
    }

    String prompt;
    try {
      prompt =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(body))
              .toString();
    } catch (CharacterCodingException exception) {
      throw invalidRequest();
    }
    if (prompt.isBlank()) {
      throw invalidRequest();
    }
    return prompt;
  }

  private ApiException invalidRequest() {
    return new ApiException(
        HttpStatus.BAD_REQUEST,
        "INVALID_REQUEST",
        "Request body must contain a nonblank UTF-8 prompt.");
  }
}
