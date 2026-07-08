package com.shark.profile.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "game_history",
    indexes = {
        @Index(name = "idx_game_history_userId", columnList = "userId")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_game_history_user_session", columnNames = {"userId", "sessionId"})
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameHistoryEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private Integer finalPosition;

    @Column(nullable = false)
    private Long sessionScore;

    @Column(nullable = false)
    private Integer maxSizeReached;

    @Column(nullable = false)
    private Instant playedAt;
}
