package ru.maltsev.primemarketbackend.account.domain;

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
import ru.maltsev.primemarketbackend.shared.PublicCodeGenerator;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_account_txs")
public class UserAccountTx {
    private static final String PUBLIC_CODE_PREFIX = "TX";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_code", nullable = false, updatable = false, length = 16)
    private String publicCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false)
    private UserAccount userAccount;

    @Column(name = "amount", nullable = false, precision = 13, scale = 4)
    private BigDecimal amount;

    @Column(name = "type", nullable = false)
    private String txType;

    @Column(name = "ref_type", nullable = false)
    private String refType;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UserAccountTx(UserAccount userAccount, BigDecimal amount, String txType, String refType, Long refId) {
        this.userAccount = userAccount;
        this.amount = amount;
        this.txType = txType;
        this.refType = refType;
        this.refId = refId;
    }

    @PrePersist
    private void onCreate() {
        if (publicCode == null) {
            publicCode = PublicCodeGenerator.generate(PUBLIC_CODE_PREFIX);
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
