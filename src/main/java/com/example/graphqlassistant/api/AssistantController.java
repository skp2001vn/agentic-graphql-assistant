package com.example.graphqlassistant.api;

import com.example.graphqlassistant.assistant.AssistantService;
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

@RestController
public class AssistantController {

  static final int MAX_REQUEST_BYTES = 100 * 1024;

  private final AssistantService assistantService;

  public AssistantController(AssistantService assistantService) {
    this.assistantService = assistantService;
  }

  @PostMapping(
      path = "/assistant",
      consumes = MediaType.TEXT_PLAIN_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
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
