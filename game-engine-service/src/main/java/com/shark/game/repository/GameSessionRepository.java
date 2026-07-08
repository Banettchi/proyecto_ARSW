package com.shark.game.repository;

import com.shark.game.model.GameSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GameSessionRepository extends JpaRepository<GameSession, UUID> {
}
