package ru.maltsev.primemarketbackend.offer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Embeddable
public class OfferDeliveryMethodId implements Serializable {
    @Column(name = "offer_id")
    private Long offerId;

    @Column(name = "delivery_method_id")
    private Long deliveryMethodId;

    protected OfferDeliveryMethodId() {}

    public OfferDeliveryMethodId(Long offerId, Long deliveryMethodId) {
        this.offerId = offerId;
        this.deliveryMethodId = deliveryMethodId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        OfferDeliveryMethodId that = (OfferDeliveryMethodId) o;
        return Objects.equals(offerId, that.offerId)
            && Objects.equals(deliveryMethodId, that.deliveryMethodId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(offerId, deliveryMethodId);
    }
}
