package com.github.bonsaidata.authorizer.controller;

import com.github.bonsaidata.authorizer.domain.TransactionStatus;
import com.github.bonsaidata.authorizer.dto.AccountResponse;
import com.github.bonsaidata.authorizer.dto.AuthorizeTransactionRequest;
import com.github.bonsaidata.authorizer.dto.CreateAccountRequest;
import com.github.bonsaidata.authorizer.dto.TransactionResponse;
import com.github.bonsaidata.authorizer.service.TransactionAuthorizerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Authorizer", description = "Account management and transaction authorization endpoints")
public class TransactionController {

    private final TransactionAuthorizerService authorizerService;

    public TransactionController(TransactionAuthorizerService authorizerService) {
        this.authorizerService = authorizerService;
    }

    @PostMapping("/accounts")
    @Operation(summary = "Create a new account", description = "Initializes an account with document validation and initial balance.")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = authorizerService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/accounts/{accountId}")
    @Operation(summary = "Get account details", description = "Retrieves current account balance and metadata by UUID.")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID accountId) {
        AccountResponse response = authorizerService.getAccount(accountId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transactions")
    @Operation(summary = "Authorize a financial transaction", description = "Processes a debit transaction enforcing Redis idempotency and PostgreSQL pessimistic write locks.")
    public ResponseEntity<TransactionResponse> authorizeTransaction(
            @Parameter(description = "Unique key to guarantee idempotency across retries", required = true, example = "tx-auth-001")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AuthorizeTransactionRequest request) {

        TransactionResponse response = authorizerService.authorize(idempotencyKey, request);

        if (response.status() == TransactionStatus.REJECTED) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
        }

        return ResponseEntity.ok(response);
    }
}
