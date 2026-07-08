package com.shark.profile.service;

import com.shark.profile.dto.GameHistoryResponse;
import com.shark.profile.dto.ProfileResponse;
import com.shark.profile.dto.RankingEntryResponse;
import com.shark.profile.dto.events.PlayerResult;
import com.shark.profile.dto.events.UserRegisteredEventPayload;
import com.shark.profile.exception.ProfileNotFoundException;
import com.shark.profile.model.GameHistoryEntry;
import com.shark.profile.model.SharkProfile;
import com.shark.profile.repository.GameHistoryRepository;
import com.shark.profile.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class ProfileService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileService.class);

    private final ProfileRepository profileRepository;
    private final GameHistoryRepository gameHistoryRepository;

    public ProfileService(ProfileRepository profileRepository, GameHistoryRepository gameHistoryRepository) {
        this.profileRepository = profileRepository;
        this.gameHistoryRepository = gameHistoryRepository;
    }

    @Transactional
    public void createInitialProfile(UserRegisteredEventPayload payload) {
        if (profileRepository.existsByUserId(payload.userId())) {
            logger.warn("Idempotencia: El perfil para userId {} ya existe, ignorando creación.", payload.userId());
            return;
        }

        SharkProfile profile = SharkProfile.builder()
                .userId(payload.userId())
                .username(payload.username())
                .sharkName(payload.username()) // Por defecto usamos el username inicial
                .colorHex("#00D2FF") // Default
                .level(1)
                .experience(0)
                .totalScore(0L)
                // TODO: Para la política de cambio cada 7 días, inicializar lastColorChangeAt = null
                .build();

        profileRepository.save(profile);
        logger.info("Perfil inicial creado para userId: {}", payload.userId());
    }

    public ProfileResponse getProfileByUserId(UUID userId) {
        SharkProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(ProfileNotFoundException::new);

        return new ProfileResponse(
                profile.getUserId(),
                profile.getSharkName(),
                profile.getColorHex(),
                profile.getLevel(),
                profile.getExperience(),
                profile.getTotalScore()
        );
    }

    @Transactional
    public void updateSharkColor(UUID userId, String newColorHex) {
        SharkProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(ProfileNotFoundException::new);

        // TODO: Validar 7 días desde lastColorChangeAt antes de actualizar
        profile.setColorHex(newColorHex);
        
        profileRepository.save(profile);
        logger.info("Color de perfil actualizado para userId: {}", userId);
    }

    @Transactional
    public void applyGameResult(PlayerResult result, UUID sessionId) {
        if (gameHistoryRepository.existsByUserIdAndSessionId(result.userId(), sessionId)) {
            logger.warn("Idempotencia: El userId {} ya tiene resultados procesados para la session {}, ignorando.", result.userId(), sessionId);
            return;
        }

        SharkProfile profile = profileRepository.findByUserId(result.userId()).orElseGet(() -> {
            // Caso borde: evento procesado antes del registro
            logger.warn("Perfil no encontrado para userId {} durante applyGameResult. Creando perfil por defecto preventivo.", result.userId());
            SharkProfile newProfile = SharkProfile.builder()
                    .userId(result.userId())
                    .username("Jugador_" + result.userId().toString().substring(0, 5))
                    .sharkName("Tiburón Anónimo")
                    .colorHex("#00D2FF")
                    .level(1)
                    .experience(0)
                    .totalScore(0L)
                    .build();
            return profileRepository.save(newProfile);
        });

        profile.setTotalScore(profile.getTotalScore() + result.score());

        GameHistoryEntry history = GameHistoryEntry.builder()
                .userId(result.userId())
                .sessionId(sessionId)
                .finalPosition(result.finalPosition())
                .sessionScore(result.score())
                .maxSizeReached(result.maxSizeReached())
                .playedAt(Instant.now())
                .build();

        gameHistoryRepository.save(history);
        profileRepository.save(profile);
    }

    public List<GameHistoryResponse> getHistory(UUID userId) {
        return gameHistoryRepository.findTop10ByUserIdOrderByPlayedAtDesc(userId).stream()
                .map(entry -> new GameHistoryResponse(
                        entry.getSessionId(),
                        entry.getPlayedAt(),
                        entry.getFinalPosition(),
                        entry.getSessionScore(),
                        entry.getMaxSizeReached()))
                .collect(Collectors.toList());
    }

    public List<RankingEntryResponse> getGlobalRanking(int limit) {
        List<SharkProfile> topProfiles = profileRepository.findAll(
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "totalScore"))).getContent();

        AtomicInteger position = new AtomicInteger(1);
        return topProfiles.stream().map(profile -> {
            int gamesPlayed = gameHistoryRepository.countByUserId(profile.getUserId()); 
            return new RankingEntryResponse(
                    position.getAndIncrement(),
                    profile.getUsername(),
                    profile.getTotalScore(),
                    gamesPlayed
            );
        }).collect(Collectors.toList());
    }

    public Integer getMyRankPosition(UUID userId) {
        SharkProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(ProfileNotFoundException::new);

        long count = profileRepository.countByTotalScoreGreaterThan(profile.getTotalScore());
        return (int) (count + 1);
    }
}
