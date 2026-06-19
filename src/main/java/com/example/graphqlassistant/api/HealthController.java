package com.example.graphqlassistant.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  @GetMapping("/health")
  @Operation(
      summary = "Check application health",
      description = "Confirms that the application started and loaded its configured schema.",
      responses =
          @ApiResponse(
              responseCode = "200",
              description = "Application is ready",
              content =
                  @Content(
                      mediaType = "application/json",
                      schema = @Schema(implementation = HealthResponse.class),
                      examples = @ExampleObject(name = "ready", value = "{\"status\":\"UP\"}"))))
  public HealthResponse health() {
    return new HealthResponse("UP");
  }
}
