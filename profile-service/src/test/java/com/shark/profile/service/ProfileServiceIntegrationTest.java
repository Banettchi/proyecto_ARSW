package com.shark.profile.service;

import com.shark.profile.dto.events.PlayerResult;
import com.shark.profile.model.GameHistoryEntry;
import com.shark.profile.model.SharkProfile;
import com.shark.profile.repository.GameHistoryRepository;
import com.shark.profile.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ProfileService.class)
public class ProfileServiceIntegrationTest {

    @Autowired
    private ProfileService profileService;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private GameHistoryRepository gameHistoryRepository;

    private UUID userId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
    }

    @Test
    void applyGameResult_success_accumulatesScoreAndSavesHistory() {
        // Arrange
        PlayerResult result1 = new PlayerResult(userId, 1, 100L, 50);
        PlayerResult result2 = new PlayerResult(userId, 2, 50L, 30);
        UUID sessionId2 = UUID.randomUUID();

        // Act - First game
        profileService.applyGameResult(result1, sessionId);
        
        // Assert - First game
        SharkProfile profile = profileRepository.findByUserId(userId).orElseThrow();
        assertThat(profile.getTotalScore()).isEqualTo(100L);
        assertThat(gameHistoryRepository.findAll()).hasSize(1);

        // Act - Second game
        profileService.applyGameResult(result2, sessionId2);

        // Assert - Second game (Score is accumulated)
        profile = profileRepository.findByUserId(userId).orElseThrow();
        assertThat(profile.getTotalScore()).isEqualTo(150L);
        assertThat(gameHistoryRepository.findAll()).hasSize(2);
    }

    @Test
    void applyGameResult_idempotency_doesNotDuplicateScoreOrHistory() {
        // Arrange
        PlayerResult result = new PlayerResult(userId, 1, 100L, 50);

        // Act - Apply same result twice with SAME session ID
        profileService.applyGameResult(result, sessionId);
        profileService.applyGameResult(result, sessionId);

        // Assert - Idempotency
        SharkProfile profile = profileRepository.findByUserId(userId).orElseThrow();
        
        // Score remains 100, not 200
        assertThat(profile.getTotalScore()).isEqualTo(100L);
        
        // Only one history entry exists
        List<GameHistoryEntry> history = gameHistoryRepository.findAll();
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getSessionId()).isEqualTo(sessionId);
    }
}
