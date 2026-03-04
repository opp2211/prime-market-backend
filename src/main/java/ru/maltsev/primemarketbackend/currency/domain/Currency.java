package ru.maltsev.primemarketbackend.currency.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "currencies")
public class Currency {
    @Id
    @Column(length = 5)
    private String code;

    @Column
    private String title;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}