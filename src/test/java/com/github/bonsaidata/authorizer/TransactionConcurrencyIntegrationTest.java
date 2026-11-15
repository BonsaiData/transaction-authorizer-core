package com.github.bonsaidata.authorizer;

import com.github.bonsaidata.authorizer.domain.TransactionStatus;
import com.github.bonsaidata.authorizer.dto.AccountResponse;
import com.github.bonsaidata.authorizer.dto.AuthorizeTransactionRequest;
import com.github.bonsaidata.authorizer.dto.CreateAccountRequest;
import com.github.bonsaidata.authorizer.dto.TransactionResponse;
import com.github.bonsaidata.authorizer.service.TransactionAuthorizerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class TransactionConcurrencyIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> postgres = new GenericContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withExposedPorts(5432)
            .withEnv("POSTGRES_DB", "authorizer_test_db")
            .withEnv("POSTGRES_USER", "test_user")
            .withEnv("POSTGRES_PASSWORD", "test_pass");

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/authorizer_test_db");
        registry.add("spring.datasource.username", () -> "test_user");
        registry.add("spring.datasource.password", () -> "test_pass");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private TransactionAuthorizerService authorizerService;

    @Test
    void shouldPreventNegativeBalanceUnderHighConcurrency() throws InterruptedException {
        AccountResponse account = authorizerService.createAccount(
                new CreateAccountRequest("99988877766", "Stress Test User", new BigDecimal("1000.00"))
        );

        int concurrentThreads = 10;
        BigDecimal purchaseAmount = new BigDecimal("200.00");

        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch readyLatch = new CountDownLatch(concurrentThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(concurrentThreads);

        List<TransactionResponse> responses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < concurrentThreads; i++) {
            final String idempotencyKey = "concurrent-tx-" + i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    TransactionResponse resp = authorizerService.authorize(
                            idempotencyKey,
                            new AuthorizeTransactionRequest(account.id(), purchaseAmount)
                    );
                    responses.add(resp);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        long approvedCount = responses.stream()
                .filter(r -> r.status() == TransactionStatus.APPROVED)
                .count();
        long rejectedCount = responses.stream()
                .filter(r -> r.status() == TransactionStatus.REJECTED)
                .count();

        assertThat(approvedCount).isEqualTo(5);
        assertThat(rejectedCount).isEqualTo(5);

        AccountResponse finalAccount = authorizerService.getAccount(account.id());
        assertThat(finalAccount.balance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldGuaranteeIdempotencyOnDuplicateRequests() {
        AccountResponse account = authorizerService.createAccount(
                new CreateAccountRequest("11122233344", "Idempotency User", new BigDecimal("500.00"))
        );

        String sameKey = "idempotent-key-" + UUID.randomUUID();
        AuthorizeTransactionRequest request = new AuthorizeTransactionRequest(account.id(), new BigDecimal("300.00"));

        TransactionResponse firstCall = authorizerService.authorize(sameKey, request);
        TransactionResponse secondCall = authorizerService.authorize(sameKey, request);

        assertThat(firstCall.transactionId()).isEqualTo(secondCall.transactionId());
        assertThat(firstCall.status()).isEqualTo(TransactionStatus.APPROVED);

        AccountResponse finalAccount = authorizerService.getAccount(account.id());
        assertThat(finalAccount.balance()).isEqualByComparingTo(new BigDecimal("200.00"));
    }
}
