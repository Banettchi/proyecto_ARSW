package com.shark.profile.repository;

import com.shark.profile.model.SharkProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<SharkProfile, UUID> {
    Optional<SharkProfile> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
    long countByTotalScoreGreaterThan(Long totalScore);
}
