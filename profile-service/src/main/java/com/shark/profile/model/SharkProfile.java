package com.shark.profile.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shark_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharkProfile {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    @Builder.Default
    private String sharkName = "Tiburón Anónimo";

    @Column(nullable = false)
    @Builder.Default
    private String colorHex = "#00D2FF";

    @Column(nullable = false)
    @Builder.Default
    private Integer level = 1;

    @Column(nullable = false)
    @Builder.Default
    private Integer experience = 0;

    @Column(nullable = false)
    @Builder.Default
    private Long totalScore = 0L;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
