package com.example.graphqlassistant.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.graphqlassistant.assistant.AssistantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AssistantController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class})
class GlobalExceptionHandlerTest {

  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AssistantService assistantService;

  @Test
  void hidesUnexpectedExceptionDetails() throws Exception {
    when(assistantService.generate(anyString()))
        .thenThrow(new IllegalStateException("sensitive internal detail"));

    mockMvc
        .perform(
            post("/assistant")
                .contentType(MediaType.TEXT_PLAIN)
                .accept(MediaType.APPLICATION_JSON)
                .content("generate a query"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().string(not(containsString("sensitive internal detail"))))
        .andExpect(content().string(not(containsString("IllegalStateException"))))
        .andExpect(jsonPath("$.status").value(500))
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.requestId").value(matchesPattern(UUID_PATTERN)))
        .andExpect(jsonPath("$.timestamp").isString())
        .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
        .andExpect(jsonPath("$.details").isEmpty());
  }

  @Test
  void preservesNotFoundStatusForUnknownRoutes() throws Exception {
    mockMvc.perform(get("/missing")).andExpect(status().isNotFound());
  }
}
