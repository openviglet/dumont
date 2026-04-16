package com.viglet.dumont.connector.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class DumApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "Key";

    private final String apiKey;

    public DumApiKeyFilter(@Value("${turing.apiKey}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestKey = request.getHeader(API_KEY_HEADER);
        if (requestKey == null) {
            requestKey = request.getParameter(API_KEY_HEADER);
        }
        if (!apiKey.equals(requestKey)) {
            response.setStatus(422);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or missing API Key\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith("/api")
                || uri.startsWith("/api/v2/ping")
                || uri.startsWith("/api/v2/discovery")
                || uri.startsWith("/api/v2/aem/status")
                || uri.startsWith("/api/v2/aem/index/")
                || uri.startsWith("/api/v2/connector/status");
    }
}
