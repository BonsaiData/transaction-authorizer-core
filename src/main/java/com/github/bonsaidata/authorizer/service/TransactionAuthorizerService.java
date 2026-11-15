package com.github.bonsaidata.authorizer.service;

import com.github.bonsaidata.authorizer.domain.Account;
import com.github.bonsaidata.authorizer.domain.Transaction;
import com.github.bonsaidata.authorizer.dto.AccountResponse;
import com.github.bonsaidata.authorizer.dto.AuthorizeTransactionRequest;
import com.github.bonsaidata.authorizer.dto.CreateAccountRequest;
import com.github.bonsaidata.authorizer.dto.TransactionResponse;
import com.github.bonsaidata.authorizer.exception.AccountNotFoundException;
import com.github.bonsaidata.authorizer.exception.DuplicateIdempotencyConflictException;
import com.github.bonsaidata.authorizer.repository.AccountRepository;
import com.github.bonsaidata.authorizer.repository.TransactionRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionAuthorizerService {

    private static final String IDEMPOTENCY_PREFIX = "idempotency:tx:";
    private static final String PROCESSING_STATE = "PROCESSING";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration COMPLETED_TTL = Duration.ofHours(24);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final StringRedisTemplate redisTemplate;

    public TransactionAuthorizerService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            StringRedisTemplate redisTemplate) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        Account account = new Account(
                UUID.randomUUID(),
                request.document(),
                request.holderName(),
                request.initialBalance()
        );
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        return AccountResponse.from(account);
    }

    @Transactional
    public TransactionResponse authorize(String idempotencyKey, AuthorizeTransactionRequest request) {
        String redisKey = IDEMPOTENCY_PREFIX + idempotencyKey;

        // idempotency key aqui! Um jeito prático do cliente não querer te esganar por fazer um pix repetido
        Optional<Transaction> existingTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTx.isPresent()) {
            return TransactionResponse.from(existingTx.get());
        }

        // Lock na chave do redis, evita de processar simultaneamente e quebrar
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, PROCESSING_STATE, LOCK_TTL);
        if (Boolean.FALSE.equals(acquired)) {
            String currentState = redisTemplate.opsForValue().get(redisKey);
            if (PROCESSING_STATE.equals(currentState)) {
                throw new DuplicateIdempotencyConflictException(idempotencyKey);
            }
            return transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .map(TransactionResponse::from)
                    .orElseThrow(() -> new DuplicateIdempotencyConflictException(idempotencyKey));
        }

        try {
            // PESSIMISTIC_WRITE lock e PostgreSQL aqui, não perde.
            Account account = accountRepository.findByIdWithLock(request.accountId())
                    .orElseThrow(() -> new AccountNotFoundException(request.accountId()));

            Transaction transaction;
            if (account.hasSufficientBalance(request.amount())) {
                account.debit(request.amount());
                accountRepository.save(account);
                transaction = Transaction.approved(account.getId(), idempotencyKey, request.amount());
            } else {
                transaction = Transaction.rejected(account.getId(), idempotencyKey, request.amount(), "INSUFFICIENT_BALANCE");
            }

            Transaction savedTx = transactionRepository.save(transaction);
            redisTemplate.opsForValue().set(redisKey, savedTx.getStatus().name(), COMPLETED_TTL);
            return TransactionResponse.from(savedTx);
        } catch (RuntimeException ex) {
            redisTemplate.delete(redisKey);
            throw ex;
        }
    }
}
