package ru.maltsev.primemarketbackend.deposit.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "deposit_methods")
public class DepositMethod {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String title;

    @Column(name = "currency_code", nullable = false, length = 16)
    private String currencyCode;

    @Column(name = "payment_details")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode paymentDetails;

    @Column(name = "auto_confirmation")
    private boolean autoConfirmation = false;

    @Column(name = "is_active")
    private boolean active = true;
}
