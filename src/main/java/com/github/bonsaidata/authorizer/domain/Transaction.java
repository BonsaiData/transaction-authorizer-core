package com.github.bonsaidata.authorizer.domain;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false, length = 64)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Transient
    private boolean isNew = true;

    protected Transaction() {}

    private Transaction(UUID accountId, String idempotencyKey, BigDecimal amount, TransactionStatus status, String rejectionReason) {
        this.id = UUID.randomUUID();
        this.accountId = accountId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.status = status;
        this.rejectionReason = rejectionReason;
        this.createdAt = OffsetDateTime.now();
        this.isNew = true;
    }

    public static Transaction approved(UUID accountId, String idempotencyKey, BigDecimal amount) {
        return new Transaction(accountId, idempotencyKey, amount, TransactionStatus.APPROVED, null);
    }

    public static Transaction rejected(UUID accountId, String idempotencyKey, BigDecimal amount, String reason) {
        return new Transaction(accountId, idempotencyKey, amount, TransactionStatus.REJECTED, reason);
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() {
        return this.isNew;
    }

    @Override
    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public BigDecimal getAmount() { return amount; }
    public TransactionStatus getStatus() { return status; }
    public String getRejectionReason() { return rejectionReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
