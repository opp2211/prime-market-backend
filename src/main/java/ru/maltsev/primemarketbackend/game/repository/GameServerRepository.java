package ru.maltsev.primemarketbackend.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.game.domain.GameServer;

import java.util.List;

public interface GameServerRepository extends JpaRepository<GameServer, Long> {
    List<GameServer> findAllByGame_IdAndActiveTrueOrderBySortOrderAsc(Long gameId);
}
