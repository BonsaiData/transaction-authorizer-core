package com.github.bonsaidata.authorizer.dto;

import com.github.bonsaidata.authorizer.domain.Account;
import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String document,
        String holderName,
        BigDecimal balance
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getDocument(),
                account.getHolderName(),
                account.getBalance()
        );
    }
}
