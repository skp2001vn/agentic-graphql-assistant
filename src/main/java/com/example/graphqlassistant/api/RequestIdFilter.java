package com.example.graphqlassistant.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

  static final String REQUEST_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    request.setAttribute(REQUEST_ID_ATTRIBUTE, UUID.randomUUID().toString());
    filterChain.doFilter(request, response);
  }
}
