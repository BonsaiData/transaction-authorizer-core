package com.github.bonsaidata.authorizer.dto;

import com.github.bonsaidata.authorizer.domain.Transaction;
import com.github.bonsaidata.authorizer.domain.TransactionStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        UUID accountId,
        String idempotencyKey,
        BigDecimal amount,
        TransactionStatus status,
        String rejectionReason,
        OffsetDateTime createdAt
) {
    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getAccountId(),
                tx.getIdempotencyKey(),
                tx.getAmount(),
                tx.getStatus(),
                tx.getRejectionReason(),
                tx.getCreatedAt()
        );
    }
}
