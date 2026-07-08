package com.shark.profile.repository;

import com.shark.profile.model.GameHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface GameHistoryRepository extends JpaRepository<GameHistoryEntry, UUID> {
    List<GameHistoryEntry> findTop10ByUserIdOrderByPlayedAtDesc(UUID userId);
    boolean existsByUserIdAndSessionId(UUID userId, UUID sessionId);
    int countByUserId(UUID userId);
}
