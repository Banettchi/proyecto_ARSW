package com.shark.profile.service;

import com.shark.profile.dto.ProfileResponse;
import com.shark.profile.dto.events.PlayerResult;
import com.shark.profile.dto.events.UserRegisteredEventPayload;
import com.shark.profile.exception.ProfileNotFoundException;
import com.shark.profile.model.GameHistoryEntry;
import com.shark.profile.model.SharkProfile;
import com.shark.profile.repository.GameHistoryRepository;
import com.shark.profile.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ======================== PRUEBAS DE CAJA GRIS / SERVICIO ========================
 *
 * Tipo: Caja Gris (Gray-Box) + Unitarias (con aislamiento de repositorios via Mockito)
 *
 * Justificación:
 * - Caja Gris: conocemos la estructura interna del ProfileService (qué repositorios
 *   llama, en qué orden, con qué argumentos), pero probamos la interfaz pública.
 * - Los repositorios JPA son mockeados con Mockito para garantizar tests sin DB.
 * - Se valida idempotencia de mensajería, aplicación de resultados y lógica de ranking.
 *
 * CORRECCIÓN (develop):
 * - PlayerResult usa (UUID, Integer, Long, Integer) - correcto
 * - ProfileCreateDto usa (UUID, username, sharkName) - 3 campos, se corrige la invocación
 * - applyResult llama a result.score() no result.sessionScore()
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceGrayBoxTest {

    @Mock
    private ProfileRepository profileRepository;
    @Mock
    private GameHistoryRepository gameHistoryRepository;

    private ProfileService profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileService(profileRepository, gameHistoryRepository);
    }

    // =================== CREATE INITIAL PROFILE ===================

    @Nested
    @DisplayName("createInitialProfile() - Idempotencia y creación")
    class CreateInitialProfileTests {

        @Test
        @DisplayName("Crea perfil si userId no existe (caso normal)")
        void createProfile_UserNotExists_SavesNewProfile() {
            UUID userId = UUID.randomUUID();
            UserRegisteredEventPayload payload =
                    new UserRegisteredEventPayload(userId, "nueva_ballena", "test@test.com", Instant.now());

            when(profileRepository.existsByUserId(userId)).thenReturn(false);
            when(profileRepository.save(any(SharkProfile.class))).thenAnswer(inv -> inv.getArgument(0));

            profileService.createInitialProfile(payload);

            ArgumentCaptor<SharkProfile> captor = ArgumentCaptor.forClass(SharkProfile.class);
            verify(profileRepository).save(captor.capture());

            SharkProfile saved = captor.getValue();
            assertEquals(userId, saved.getUserId());
            assertEquals("nueva_ballena", saved.getUsername());
            // sharkName por defecto = username (según ProfileService.createInitialProfile)
            assertEquals("nueva_ballena", saved.getSharkName(),
                    "sharkName debe iniciar igual que username según lógica de negocio");
            assertEquals("#00D2FF", saved.getColorHex(), "ColorHex inicial debe ser azul por defecto");
            assertEquals(1, saved.getLevel());
            assertEquals(0, saved.getExperience());
            assertEquals(0L, saved.getTotalScore());
        }

        @Test
        @DisplayName("Idempotencia: no crea perfil si userId ya existe (mensaje duplicado)")
        void createProfile_UserAlreadyExists_DoesNothing() {
            UUID userId = UUID.randomUUID();
            UserRegisteredEventPayload payload =
                    new UserRegisteredEventPayload(userId, "existente", "ex@test.com", Instant.now());

            when(profileRepository.existsByUserId(userId)).thenReturn(true);

            profileService.createInitialProfile(payload);

            // Verificar que NO se llamó save (idempotencia garantizada)
            verify(profileRepository, never()).save(any());
        }
    }

    // =================== GET PROFILE ===================

    @Nested
    @DisplayName("getProfileByUserId() - Consulta y excepciones")
    class GetProfileTests {

        @Test
        @DisplayName("Perfil encontrado: retorna ProfileResponse con datos correctos")
        void getProfile_Found_ReturnsMappedDto() {
            UUID userId = UUID.randomUUID();
            SharkProfile profile = SharkProfile.builder()
                    .userId(userId)
                    .username("gamer")
                    .sharkName("TestShark")
                    .colorHex("#FF5733")
                    .level(7)
                    .experience(3400)
                    .totalScore(18500L)
                    .build();

            when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

            ProfileResponse response = profileService.getProfileByUserId(userId);

            assertEquals(userId, response.userId());
            assertEquals("TestShark", response.sharkName());
            assertEquals("#FF5733", response.colorHex());
            assertEquals(7, response.level());
            assertEquals(18500L, response.totalScore());
        }

        @Test
        @DisplayName("Perfil no encontrado: lanza ProfileNotFoundException (HTTP 404)")
        void getProfile_NotFound_Throws() {
            UUID userId = UUID.randomUUID();
            when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThrows(ProfileNotFoundException.class, () -> profileService.getProfileByUserId(userId));
        }
    }

    // =================== UPDATE COLOR ===================

    @Nested
    @DisplayName("updateSharkColor() - Actualización (HU-08/KAN-22)")
    class UpdateColorTests {

        @Test
        @DisplayName("Actualización de color exitosa")
        void updateColor_ProfileExists_UpdatesHex() {
            UUID userId = UUID.randomUUID();
            SharkProfile profile = SharkProfile.builder()
                    .userId(userId).colorHex("#00D2FF").username("test")
                    .sharkName("Mi Tiburón")
                    .build();

            when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            profileService.updateSharkColor(userId, "#FF0000");

            assertEquals("#FF0000", profile.getColorHex());
            verify(profileRepository).save(profile);
        }

        @Test
        @DisplayName("Actualizar color de perfil inexistente lanza ProfileNotFoundException")
        void updateColor_ProfileNotFound_Throws() {
            UUID userId = UUID.randomUUID();
            when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThrows(ProfileNotFoundException.class,
                    () -> profileService.updateSharkColor(userId, "#AABBCC"));
        }
    }

    // =================== APPLY GAME RESULT ===================

    @Nested
    @DisplayName("applyGameResult() - Procesamiento de resultados de partida")
    class ApplyGameResultTests {

        @Test
        @DisplayName("Resultado aplicado: suma score al perfil y guarda historial")
        void applyResult_FirstTime_UpdatesScoreAndSavesHistory() {
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();

            SharkProfile profile = SharkProfile.builder()
                    .userId(userId).totalScore(1000L).username("gamer")
                    .sharkName("gamer")
                    .build();

            when(gameHistoryRepository.existsByUserIdAndSessionId(userId, sessionId)).thenReturn(false);
            when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
            when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(gameHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // PlayerResult(UUID userId, Integer finalPosition, Long score, Integer maxSizeReached)
            PlayerResult result = new PlayerResult(userId, 1, 500L, 45);
            profileService.applyGameResult(result, sessionId);

            // score() = 500L, totalScore anterior = 1000L → nuevo totalScore = 1500L
            assertEquals(1500L, profile.getTotalScore(), "Score total: 1000 + 500 = 1500");

            ArgumentCaptor<GameHistoryEntry> histCaptor = ArgumentCaptor.forClass(GameHistoryEntry.class);
            verify(gameHistoryRepository).save(histCaptor.capture());
            assertEquals(1, histCaptor.getValue().getFinalPosition());
            // El servicio guarda result.score() en sessionScore
            assertEquals(500L, histCaptor.getValue().getSessionScore());
            assertEquals(45, histCaptor.getValue().getMaxSizeReached());
        }

        @Test
        @DisplayName("Idempotencia: resultado ya procesado para esa sesión -> no se aplica dos veces")
        void applyResult_Duplicate_DoesNothing() {
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();

            when(gameHistoryRepository.existsByUserIdAndSessionId(userId, sessionId)).thenReturn(true);

            profileService.applyGameResult(new PlayerResult(userId, 1, 500L, 45), sessionId);

            verify(profileRepository, never()).save(any());
            verify(gameHistoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("Caso borde: perfil no existe al procesar resultado -> crea perfil por defecto")
        void applyResult_ProfileNotFound_CreatesDefaultProfile() {
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();

            when(gameHistoryRepository.existsByUserIdAndSessionId(userId, sessionId)).thenReturn(false);
            when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(profileRepository.save(any())).thenAnswer(inv -> {
                SharkProfile p = inv.getArgument(0);
                if (p.getTotalScore() == null) p.setTotalScore(0L);
                return p;
            });
            when(gameHistoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertDoesNotThrow(() ->
                    profileService.applyGameResult(new PlayerResult(userId, 3, 200L, 20), sessionId));

            verify(profileRepository, atLeast(1)).save(any());
        }
    }

    // =================== MY RANK POSITION ===================

    @Nested
    @DisplayName("getMyRankPosition() - Posición en ranking global")
    class RankingTests {

        @Test
        @DisplayName("Posición calculada correctamente: jugadores con más score + 1")
        void getRankPosition_CalculatesCorrectly() {
            UUID userId = UUID.randomUUID();
            SharkProfile profile = SharkProfile.builder()
                    .userId(userId).totalScore(5000L).username("test")
                    .sharkName("test")
                    .build();

            when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
            when(profileRepository.countByTotalScoreGreaterThan(5000L)).thenReturn(3L);

            Integer pos = profileService.getMyRankPosition(userId);

            assertEquals(4, pos, "3 jugadores con más score -> posición 4");
        }

        @Test
        @DisplayName("Usuario con el score más alto -> posición 1")
        void getRankPosition_TopPlayer_Returns1() {
            UUID userId = UUID.randomUUID();
            SharkProfile profile = SharkProfile.builder()
                    .userId(userId).totalScore(99999L).username("top")
                    .sharkName("top")
                    .build();

            when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));
            when(profileRepository.countByTotalScoreGreaterThan(99999L)).thenReturn(0L);

            assertEquals(1, profileService.getMyRankPosition(userId));
        }
    }
}
