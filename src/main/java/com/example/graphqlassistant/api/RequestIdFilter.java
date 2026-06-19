package com.example.graphqlassistant.api;

import com.example.graphqlassistant.logging.AssistantRequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID_HEADER = "X-Request-ID";

  static final String REQUEST_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

  private static final Logger LOGGER = LoggerFactory.getLogger(RequestIdFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String requestId = requestId(request);
    long startedAt = System.nanoTime();
    request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    AssistantRequestContext.start(requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      if ("/assistant".equals(request.getRequestURI())) {
        Object errorCategory =
            request.getAttribute(GlobalExceptionHandler.ERROR_CATEGORY_ATTRIBUTE);
        LOGGER
            .atInfo()
            .addKeyValue("event", "assistant_request_completed")
            .addKeyValue("requestId", requestId)
            .addKeyValue("selectedAgent", AssistantRequestContext.selectedAgent())
            .addKeyValue("status", response.getStatus())
            .addKeyValue("latencyMs", (System.nanoTime() - startedAt) / 1_000_000)
            .addKeyValue("errorCategory", errorCategory == null ? "NONE" : errorCategory)
            .log("Assistant request completed");
      }
      AssistantRequestContext.clear();
    }
  }

  private String requestId(HttpServletRequest request) {
    String supplied = request.getHeader(REQUEST_ID_HEADER);
    if (supplied != null && supplied.matches("[A-Za-z0-9._-]{1,128}")) {
      return supplied;
    }
    return UUID.randomUUID().toString();
  }
}
