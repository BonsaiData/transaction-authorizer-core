package com.github.bonsaidata.authorizer;

import org.springframework.boot.SpringApplication;

public class TestTransactionAuthorizerCoreApplication {

    public static void main(String[] args) {
        SpringApplication.from(TransactionAuthorizerCoreApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
