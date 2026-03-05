package ru.maltsev.primemarketbackend.game.domain;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameCategoryId implements Serializable {

    private Long gameId;
    private Long categoryId;
}
