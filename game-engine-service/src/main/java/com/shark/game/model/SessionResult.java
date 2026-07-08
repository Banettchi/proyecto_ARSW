package com.shark.game.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "session_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResult {
    
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;
    
    @Column(nullable = false)
    private UUID sessionId; // FK lógica a GameSession
    
    @Column(nullable = false)
    private UUID userId;
    
    @Column(nullable = false)
    private Integer finalPosition;
    
    @Column(nullable = false)
    private Long score;
    
    @Column(nullable = false)
    private Integer maxSizeReached;
    
    @Column(nullable = false)
    private Integer resourcesConsumed;
    
    @Column(nullable = false)
    private Integer rivalsEliminated;
    
    @Column(nullable = false)
    private Boolean survived;
}
