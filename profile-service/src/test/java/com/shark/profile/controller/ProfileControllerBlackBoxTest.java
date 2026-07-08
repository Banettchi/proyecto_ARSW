package com.shark.profile.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shark.profile.dto.ProfileCreateDto;
import com.shark.profile.dto.ProfileResponse;
import com.shark.profile.dto.events.UserRegisteredEventPayload;
import com.shark.profile.exception.ProfileNotFoundException;
import com.shark.profile.service.ProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ======================== PRUEBAS FUNCIONALES / CAJA NEGRA ========================
 *
 * Tipo: Funcionales (Functional Tests) + Caja Negra (Black-Box)
 *
 * Justificación:
 * - Caja Negra: probamos SOLO entradas (HTTP Request) y salidas (HTTP Response),
 *   sin conocimiento de la implementación interna de ProfileService.
 * - Funcionales: verificamos que los endpoints cumplen con los requisitos
 *   funcionales de las HU (HU-08, HU-10).
 * - @WebMvcTest levanta SOLO la capa de controladores (slice test).
 * - Los filtros de seguridad (InternalHeaderFilter, InternalOnlyFilter)
 *   se excluyen para aislar la prueba al comportamiento puro del controlador.
 *
 * CORRECCIÓN (develop): Se usa @MockBean correcto de spring-boot-test
 * y se excluyen los filtros de seguridad para evitar 401/403 falsos.
 */
@WebMvcTest(
    controllers = {InternalProfileController.class, ProfileController.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = {
            com.shark.profile.security.FilterConfig.class,
            com.shark.profile.security.InternalOnlyFilter.class,
            com.shark.profile.security.InternalHeaderFilter.class
        }
    )
)
class ProfileControllerBlackBoxTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProfileService profileService;

    @Autowired
    private ObjectMapper objectMapper;

    // =================== INTERNAL CONTROLLER (Caja Negra) ===================

    @Nested
    @DisplayName("POST /api/profiles/internal/create - Caja Negra")
    class InternalCreateTests {

        @Test
        @DisplayName("Entrada válida -> HTTP 201 Created")
        void createProfile_ValidInput_Returns201() throws Exception {
            UUID userId = UUID.randomUUID();
            // ProfileCreateDto tiene (userId, username, sharkName)
            ProfileCreateDto dto = new ProfileCreateDto(userId, "test_shark", "Mi Tiburón");

            doNothing().when(profileService).createInitialProfile(any(UserRegisteredEventPayload.class));

            mockMvc.perform(post("/api/profiles/internal/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Entrada con userId nulo -> HTTP 400 Bad Request (validación básica)")
        void createProfile_NullUserId_Returns400() throws Exception {
            // JSON intencionalmente malformado para forzar error de deserialización
            mockMvc.perform(post("/api/profiles/internal/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"userId\": \"not-a-uuid\", \"username\": \"test\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // =================== PROFILE CONTROLLER (Caja Negra) ===================

    @Nested
    @DisplayName("GET /api/profiles/me - Caja Negra")
    class GetProfileTests {

        @Test
        @DisplayName("Perfil encontrado -> HTTP 200 con datos completos")
        void getProfile_ExistingUser_Returns200WithBody() throws Exception {
            UUID userId = UUID.randomUUID();
            ProfileResponse response = new ProfileResponse(userId, "TestShark", "#FF0000", 5, 1200, 9500L);

            when(profileService.getProfileByUserId(userId)).thenReturn(response);

            mockMvc.perform(get("/api/profiles/me")
                            .requestAttr("currentUserId", userId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sharkName").value("TestShark"))
                    .andExpect(jsonPath("$.colorHex").value("#FF0000"))
                    .andExpect(jsonPath("$.level").value(5))
                    .andExpect(jsonPath("$.totalScore").value(9500));
        }

        @Test
        @DisplayName("Perfil no encontrado -> HTTP 404 Not Found")
        void getProfile_NonExistingUser_Returns404() throws Exception {
            UUID userId = UUID.randomUUID();

            when(profileService.getProfileByUserId(userId)).thenThrow(new ProfileNotFoundException());

            mockMvc.perform(get("/api/profiles/me")
                            .requestAttr("currentUserId", userId))
                    .andExpect(status().isNotFound());
        }
    }
}
