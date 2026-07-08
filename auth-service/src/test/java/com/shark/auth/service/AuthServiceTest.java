package com.shark.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shark.auth.dto.RegisterRequest;
import com.shark.auth.exception.UsernameAlreadyExistsException;
import com.shark.auth.model.OutboxEvent;
import com.shark.auth.model.OutboxStatus;
import com.shark.auth.model.User;
import com.shark.auth.repository.OutboxEventRepository;
import com.shark.auth.repository.UserRepository;
import com.shark.auth.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest request;

    @BeforeEach
    void setUp() {
        request = new RegisterRequest("shark_killer99", "player@example.com", "Password123");
    }

    @Test
    void register_success_savesUserAndOutboxEvent() throws JsonProcessingException {
        // Arrange
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed_password");
        
        User savedUser = new User();
        savedUser.setId(UUID.randomUUID());
        savedUser.setUsername(request.username());
        savedUser.setEmail(request.email());
        savedUser.setPasswordHash("hashed_password");

        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"payload\": \"json\"}");
        when(jwtProvider.generateToken(any(User.class))).thenReturn("mocked.jwt.token");

        // Act
        authService.register(request);

        // Assert (a) se guarda el User
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(request.username(), userCaptor.getValue().getUsername());

        // Assert (b) se guarda el OutboxEvent con status PENDING
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(eventCaptor.capture());
        OutboxEvent savedEvent = eventCaptor.getValue();
        assertEquals("UserRegisteredEvent", savedEvent.getEventType());
        assertEquals("User", savedEvent.getAggregateType());
        assertEquals(savedUser.getId(), savedEvent.getAggregateId());
        assertEquals(OutboxStatus.PENDING, savedEvent.getStatus());
    }

    @Test
    void register_usernameExists_throwsExceptionAndSavesNothing() {
        // Arrange
        when(userRepository.existsByUsername(request.username())).thenReturn(true);

        // Act & Assert (c) si el username ya existe se lanza la excepción correcta
        assertThrows(UsernameAlreadyExistsException.class, () -> authService.register(request));

        // Assert que no se guardó nada
        verify(userRepository, never()).save(any(User.class));
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }
}
