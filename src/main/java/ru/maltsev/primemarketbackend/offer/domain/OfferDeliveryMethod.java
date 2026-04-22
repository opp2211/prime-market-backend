package ru.maltsev.primemarketbackend.offer.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.maltsev.primemarketbackend.delivery.domain.DeliveryMethod;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "offer_delivery_methods")
public class OfferDeliveryMethod {
    @EmbeddedId
    private OfferDeliveryMethodId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("offerId")
    @JoinColumn(name = "offer_id", nullable = false)
    private Offer offer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("deliveryMethodId")
    @JoinColumn(name = "delivery_method_id", nullable = false)
    private DeliveryMethod deliveryMethod;

    public OfferDeliveryMethod(Offer offer, DeliveryMethod deliveryMethod) {
        this.offer = offer;
        this.deliveryMethod = deliveryMethod;
        this.id = new OfferDeliveryMethodId(offer.getId(), deliveryMethod.getId());
    }
}
