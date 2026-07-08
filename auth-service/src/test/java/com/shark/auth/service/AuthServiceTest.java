package com.shark.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shark.auth.dto.AuthResponse;
import com.shark.auth.dto.LoginRequest;
import com.shark.auth.dto.RegisterRequest;
import com.shark.auth.exception.*;
import com.shark.auth.model.OutboxEvent;
import com.shark.auth.model.OutboxStatus;
import com.shark.auth.model.User;
import com.shark.auth.repository.OutboxEventRepository;
import com.shark.auth.repository.UserRepository;
import com.shark.auth.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ======================== PRUEBAS UNITARIAS / CAJA BLANCA ========================
 * 
 * Tipo: Unitarias (Unit Tests) + Caja Blanca (White-Box)
 * 
 * Justificación:
 * - Se prueba la lógica interna de AuthService con pleno conocimiento del código.
 * - Se aíslan todas las dependencias externas (DB, JWT) mediante Mockito.
 * - Se cubren ramas de decisión internas: username duplicado, email duplicado,
 *   formato de username inválido, contraseña débil, credenciales incorrectas.
 * - Se verifica la cobertura de caminos (path coverage) internos del método register().
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        authService = new AuthService(userRepository, outboxEventRepository, passwordEncoder, jwtProvider, objectMapper);
    }

    // =================== REGISTER ===================

    @Nested
    @DisplayName("register() - Caja Blanca: Ramas de validación")
    class RegisterTests {

        @Test
        @DisplayName("Registro exitoso: crea usuario, outbox event y retorna JWT")
        void register_HappyPath_ReturnsAuthResponseWithToken() {
            RegisterRequest req = new RegisterRequest("shark_player", "player@test.com", "SecurePass123");

            when(userRepository.existsByUsername("shark_player")).thenReturn(false);
            when(userRepository.existsByEmail("player@test.com")).thenReturn(false);
            when(passwordEncoder.encode("SecurePass123")).thenReturn("$2a$10$hashedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });
            when(jwtProvider.generateToken(any(User.class))).thenReturn("mocked.jwt.token");
            when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

            AuthResponse response = authService.register(req);

            assertNotNull(response);
            assertEquals("mocked.jwt.token", response.token());
            assertEquals("shark_player", response.username());
            assertNotNull(response.userId());

            // Verificar que el OutboxEvent fue creado con estado PENDING
            ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
            verify(outboxEventRepository).save(outboxCaptor.capture());
            assertEquals(OutboxStatus.PENDING, outboxCaptor.getValue().getStatus());
            assertEquals("UserRegisteredEvent", outboxCaptor.getValue().getEventType());
        }

        @Test
        @DisplayName("Rama: Username duplicado lanza UsernameAlreadyExistsException")
        void register_DuplicateUsername_ThrowsException() {
            RegisterRequest req = new RegisterRequest("existing_user", "new@test.com", "SecurePass123");
            when(userRepository.existsByUsername("existing_user")).thenReturn(true);

            assertThrows(UsernameAlreadyExistsException.class, () -> authService.register(req));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rama: Email duplicado lanza EmailAlreadyExistsException")
        void register_DuplicateEmail_ThrowsException() {
            RegisterRequest req = new RegisterRequest("new_user", "existing@test.com", "SecurePass123");
            when(userRepository.existsByUsername("new_user")).thenReturn(false);
            when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

            assertThrows(EmailAlreadyExistsException.class, () -> authService.register(req));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rama: Username con caracteres especiales lanza InvalidUsernameFormatException")
        void register_InvalidUsernameFormat_ThrowsException() {
            RegisterRequest req = new RegisterRequest("user@invalid!", "test@test.com", "SecurePass123");
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);

            assertThrows(InvalidUsernameFormatException.class, () -> authService.register(req));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Rama: Contraseña menor a 8 caracteres lanza WeakPasswordException")
        void register_WeakPassword_ThrowsException() {
            RegisterRequest req = new RegisterRequest("valid_user", "test@test.com", "short");
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);

            assertThrows(WeakPasswordException.class, () -> authService.register(req));
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Verifica que el password se hashea antes de guardar (nunca plaintext)")
        void register_PasswordIsHashed_NeverStoredPlaintext() {
            RegisterRequest req = new RegisterRequest("hashed_user", "hash@test.com", "MySecretPass");
            when(userRepository.existsByUsername(anyString())).thenReturn(false);
            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode("MySecretPass")).thenReturn("$2a$10$encodedHash");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(UUID.randomUUID());
                return u;
            });
            when(jwtProvider.generateToken(any())).thenReturn("tok");
            when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            authService.register(req);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertEquals("$2a$10$encodedHash", userCaptor.getValue().getPasswordHash());
            assertNotEquals("MySecretPass", userCaptor.getValue().getPasswordHash());
        }
    }

    // =================== LOGIN ===================

    @Nested
    @DisplayName("login() - Caja Blanca: Ramas de autenticación")
    class LoginTests {

        @Test
        @DisplayName("Login exitoso con credenciales válidas")
        void login_ValidCredentials_ReturnsToken() {
            User user = new User();
            user.setId(UUID.randomUUID());
            user.setUsername("shark_player");
            user.setEmail("player@test.com");
            user.setPasswordHash("$2a$10$hashedPassword");

            when(userRepository.findByEmail("player@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("SecurePass123", "$2a$10$hashedPassword")).thenReturn(true);
            when(jwtProvider.generateToken(user)).thenReturn("valid.jwt.token");

            LoginRequest req = new LoginRequest("player@test.com", "SecurePass123");
            AuthResponse response = authService.login(req);

            assertEquals("valid.jwt.token", response.token());
            assertEquals("shark_player", response.username());
        }

        @Test
        @DisplayName("Rama: Email no encontrado lanza InvalidCredentialsException")
        void login_EmailNotFound_ThrowsException() {
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());
            LoginRequest req = new LoginRequest("ghost@test.com", "password");

            assertThrows(InvalidCredentialsException.class, () -> authService.login(req));
        }

        @Test
        @DisplayName("Rama: Password incorrecto lanza InvalidCredentialsException")
        void login_WrongPassword_ThrowsException() {
            User user = new User();
            user.setPasswordHash("$2a$10$correctHash");
            when(userRepository.findByEmail("player@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPassword", "$2a$10$correctHash")).thenReturn(false);

            LoginRequest req = new LoginRequest("player@test.com", "wrongPassword");
            assertThrows(InvalidCredentialsException.class, () -> authService.login(req));
        }
    }
}
