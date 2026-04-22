package ru.maltsev.primemarketbackend.offer.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.maltsev.primemarketbackend.context.domain.ContextDimension;
import ru.maltsev.primemarketbackend.context.domain.ContextDimensionValue;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "offer_context_values")
public class OfferContextValue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private Offer offer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "context_dimension_id", nullable = false)
    private ContextDimension contextDimension;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "context_dimension_value_id", nullable = false)
    private ContextDimensionValue contextDimensionValue;

    public OfferContextValue(Offer offer, ContextDimension dimension, ContextDimensionValue value) {
        this.offer = offer;
        this.contextDimension = dimension;
        this.contextDimensionValue = value;
    }
}
