package ru.maltsev.primemarketbackend.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.game.domain.GameCategory;
import ru.maltsev.primemarketbackend.game.domain.GameCategoryId;

import java.util.List;

public interface GameCategoryRepository extends JpaRepository<GameCategory, GameCategoryId> {
    List<GameCategory> findAllByGame_IdAndActiveTrueOrderBySortOrderAsc(Long gameId);
}
