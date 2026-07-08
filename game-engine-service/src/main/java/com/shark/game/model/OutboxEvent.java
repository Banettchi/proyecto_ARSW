package com.shark.game.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {
    
    public enum EventStatus { PENDING, PUBLISHED, FAILED }

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;
    
    @Column(nullable = false)
    private String aggregateType;
    
    @Column(nullable = false)
    private String aggregateId;
    
    @Column(nullable = false)
    private String eventType;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;
    
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    
    private Instant publishedAt;
    
    @Builder.Default
    private Integer retryCount = 0;
    
    @Enumerated(EnumType.ORDINAL)
    @Builder.Default
    @Column(nullable = false)
    private EventStatus status = EventStatus.PENDING;
}
