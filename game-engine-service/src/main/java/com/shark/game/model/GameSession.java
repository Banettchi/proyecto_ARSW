package com.shark.game.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSession {
    
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;
    
    @Column(nullable = false)
    private String roomId;
    
    @Column(nullable = false)
    private Instant startedAt;
    
    @Column
    private Instant endedAt;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;
    
    @Column
    private String endTrigger; // "TIME_LIMIT" o "LAST_PLAYER"
}
