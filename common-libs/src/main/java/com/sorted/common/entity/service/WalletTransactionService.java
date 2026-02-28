package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.WalletTransactionEntity;
import com.sorted.common.enums.TxnType;
import com.sorted.common.enums.WalletTxnSource;
import com.sorted.common.repository.mongo.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WalletTransactionService {

    private final WalletTransactionRepository repository;

    public WalletTransactionEntity createTransaction(String walletId, long amount, long newBalance, TxnType type, WalletTxnSource source) {
        WalletTransactionEntity transaction = WalletTransactionEntity.builder()
                .walletId(walletId)
                .amount(amount)
                .balance(newBalance)
                .type(type)
                .source(source)
                .build();
        return repository.save(transaction);
    }
}
