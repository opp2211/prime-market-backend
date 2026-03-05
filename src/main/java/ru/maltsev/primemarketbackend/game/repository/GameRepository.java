package ru.maltsev.primemarketbackend.game.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.game.domain.Game;

public interface GameRepository extends JpaRepository<Game, Long> {
    List<Game> findAllByActiveTrueOrderBySortOrderAsc();
}
