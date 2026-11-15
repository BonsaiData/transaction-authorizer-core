package com.github.bonsaidata.authorizer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank @Size(min = 11, max = 14) String document,
        @NotBlank @Size(max = 120) String holderName,
        @NotNull @DecimalMin(value = "0.00") BigDecimal initialBalance
) {}
