package ru.maltsev.primemarketbackend.tradefield.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.maltsev.primemarketbackend.category.domain.Category;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "category_trade_field_configs")
public class CategoryTradeFieldConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "field_slug", nullable = false, length = 64)
    private String fieldSlug;

    @Column(name = "is_visible", nullable = false)
    private boolean visible = true;

    @Column(name = "is_required", nullable = false)
    private boolean required = false;

    @Column(name = "is_multiselect", nullable = false)
    private boolean multiselect = false;

    @Column(name = "default_value_text", length = 255)
    private String defaultValueText;

    @Column(name = "default_value_number", precision = 18, scale = 8)
    private BigDecimal defaultValueNumber;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
