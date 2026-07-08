package com.shark.game.repository;

import com.shark.game.model.SessionResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionResultRepository extends JpaRepository<SessionResult, UUID> {
    List<SessionResult> findBySessionId(UUID sessionId);
}
