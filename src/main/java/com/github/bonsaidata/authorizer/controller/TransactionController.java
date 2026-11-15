package com.github.bonsaidata.authorizer.controller;

import com.github.bonsaidata.authorizer.domain.TransactionStatus;
import com.github.bonsaidata.authorizer.dto.AccountResponse;
import com.github.bonsaidata.authorizer.dto.AuthorizeTransactionRequest;
import com.github.bonsaidata.authorizer.dto.CreateAccountRequest;
import com.github.bonsaidata.authorizer.dto.TransactionResponse;
import com.github.bonsaidata.authorizer.service.TransactionAuthorizerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TransactionController {

    private final TransactionAuthorizerService authorizerService;

    public TransactionController(TransactionAuthorizerService authorizerService) {
        this.authorizerService = authorizerService;
    }

    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authorizerService.createAccount(request));
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(authorizerService.getAccount(id));
    }

    @PostMapping("/transactions")
    public ResponseEntity<TransactionResponse> authorizeTransaction(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AuthorizeTransactionRequest request) {
        TransactionResponse response = authorizerService.authorize(idempotencyKey, request);
        HttpStatus status = response.status() == TransactionStatus.APPROVED
                ? HttpStatus.OK
                : HttpStatus.valueOf(422);
        return ResponseEntity.status(status).body(response);
    }
}
