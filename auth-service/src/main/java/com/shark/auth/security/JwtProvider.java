package com.shark.auth.security;

import com.shark.auth.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtProvider.class);
    private static final long JWT_EXPIRATION_MS = 24 * 60 * 60 * 1000L; // 24 hours

    private final SecretKey key;

    // IMPORTANTE: En producción, jwt.secret debe inyectarse vía variable de entorno (ej. JWT_SECRET)
    // desde un Secret Manager (AWS Secrets Manager, HashiCorp Vault), NUNCA debe estar hardcodeado en el código.
    public JwtProvider(@Value("${jwt.secret}") String secret) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + JWT_EXPIRATION_MS);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            logger.warn("El token JWT ha expirado: {}", e.getMessage());
        } catch (JwtException e) {
            logger.warn("Token JWT inválido o manipulado: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("El string del token JWT está vacío o es nulo: {}", e.getMessage());
        }
        return false;
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return UUID.fromString(claims.getSubject());
    }
}
