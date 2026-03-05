package ru.maltsev.primemarketbackend.game.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.maltsev.primemarketbackend.game.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByGame_IdAndCategory_IdAndActiveTrue(Long gameId, Long categoryId);
}
