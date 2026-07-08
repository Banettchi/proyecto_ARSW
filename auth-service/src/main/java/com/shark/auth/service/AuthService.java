package com.shark.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shark.auth.dto.AuthResponse;
import com.shark.auth.dto.LoginRequest;
import com.shark.auth.dto.RegisterRequest;
import com.shark.auth.dto.events.UserRegisteredEventPayload;
import com.shark.auth.exception.*;
import com.shark.auth.model.OutboxEvent;
import com.shark.auth.model.OutboxStatus;
import com.shark.auth.model.User;
import com.shark.auth.repository.OutboxEventRepository;
import com.shark.auth.repository.UserRepository;
import com.shark.auth.security.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    public AuthService(UserRepository userRepository, OutboxEventRepository outboxEventRepository, 
                       PasswordEncoder passwordEncoder, JwtProvider jwtProvider, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.objectMapper = objectMapper;
        // Registramos el módulo JavaTime para serializar Instant correctamente
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new UsernameAlreadyExistsException();
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new EmailAlreadyExistsException();
        }

        if (!req.username().matches("^[a-zA-Z0-9_]+$")) {
            throw new InvalidUsernameFormatException();
        }
        if (req.password().length() < 8) {
            throw new WeakPasswordException();
        }

        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user = userRepository.save(user);

        UserRegisteredEventPayload eventPayload = new UserRegisteredEventPayload(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                Instant.now()
        );

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(eventPayload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error serializando evento de outbox", e);
        }

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("User");
        outboxEvent.setAggregateId(user.getId());
        outboxEvent.setEventType("UserRegisteredEvent");
        outboxEvent.setPayload(payloadJson);
        outboxEvent.setStatus(OutboxStatus.PENDING);
        outboxEventRepository.save(outboxEvent);

        String token = jwtProvider.generateToken(user);
        return new AuthResponse(token, user.getId(), user.getUsername());
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtProvider.generateToken(user);
        return new AuthResponse(token, user.getId(), user.getUsername());
    }
}
