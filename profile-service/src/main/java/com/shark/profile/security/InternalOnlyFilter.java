package com.shark.profile.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/*
 * Documentación de Seguridad:
 * Este filtro protege los endpoints de la API de comunicación directa entre microservicios
 * (Server-to-Server). Esto es una medida mínima de seguridad usando un "shared secret".
 * En un entorno de producción real, esto debería resolverse a través de mTLS (Mutual TLS)
 * o manteniendo estos servicios en una red aislada sin exposición de puertos externos
 * (ej. Docker Overlay Networks o Kubernetes Network Policies / Service Mesh).
 * Sin embargo, para el alcance académico de 6 semanas, el enfoque de shared-secret
 * es suficientemente robusto, simple y explícito.
 */
public class InternalOnlyFilter extends OncePerRequestFilter {

    private final String internalSecret;

    public InternalOnlyFilter(String internalSecret) {
        this.internalSecret = internalSecret;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String secretHeader = request.getHeader("X-Internal-Secret");

        if (secretHeader == null || !secretHeader.equals(internalSecret)) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"message\": \"Acceso interno no autorizado\"}");
            response.getWriter().flush();
            return;
        }

        filterChain.doFilter(request, response);
    }
}
