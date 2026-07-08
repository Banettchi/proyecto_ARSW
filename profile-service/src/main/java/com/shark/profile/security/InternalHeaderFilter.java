package com.shark.profile.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

public class InternalHeaderFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Evitamos que este filtro se aplique a rutas internas y al healthcheck
        return path.startsWith("/api/profiles/internal") || path.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userIdHeader = request.getHeader("X-User-Id");

        if (userIdHeader == null || userIdHeader.isBlank()) {
            sendErrorResponse(response, "Falta header X-User-Id valido");
            return;
        }

        try {
            UUID userId = UUID.fromString(userIdHeader);
            request.setAttribute("currentUserId", userId);
            filterChain.doFilter(request, response);
        } catch (IllegalArgumentException e) {
            sendErrorResponse(response, "Falta header X-User-Id valido");
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\": \"" + message + "\"}");
        response.getWriter().flush();
    }
}
