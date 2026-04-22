package ru.maltsev.primemarketbackend.offer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.maltsev.primemarketbackend.attribute.domain.CategoryAttribute;
import ru.maltsev.primemarketbackend.attribute.domain.CategoryAttributeOption;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "offer_attribute_values")
public class OfferAttributeValue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private Offer offer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_attribute_id", nullable = false)
    private CategoryAttribute categoryAttribute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_attribute_option_id")
    private CategoryAttributeOption categoryAttributeOption;

    @Column(name = "value_text")
    private String valueText;

    @Column(name = "value_number", precision = 18, scale = 8)
    private BigDecimal valueNumber;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    public OfferAttributeValue(
        Offer offer,
        CategoryAttribute attribute,
        CategoryAttributeOption option,
        String valueText,
        BigDecimal valueNumber,
        Boolean valueBoolean
    ) {
        this.offer = offer;
        this.categoryAttribute = attribute;
        this.categoryAttributeOption = option;
        this.valueText = valueText;
        this.valueNumber = valueNumber;
        this.valueBoolean = valueBoolean;
    }
}
