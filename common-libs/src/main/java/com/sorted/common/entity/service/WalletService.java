package com.sorted.common.entity.service;

import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.WalletEntity;
import com.sorted.common.entity.mongo.WalletTransactionEntity;
import com.sorted.common.enums.TxnType;
import com.sorted.common.enums.WalletTxnSource;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.repository.mongo.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class WalletService extends GenericEntityServiceImpl<String, WalletEntity, WalletRepository> {

    private final WalletRepository repository;
    private final WalletTransactionService walletTransactionService;

    @Override
    protected Class<WalletRepository> getRepoClass() {
        return WalletRepository.class;
    }

    @Override
    protected void validateBeforeCreate(WalletEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeUpdate(String id, WalletEntity inE) throws RuntimeException {

    }

    @Override
    protected void validateBeforeDelete(String id) throws RuntimeException {

    }

    @Transactional
    public void creditAmount(String userId, long amount, WalletTxnSource source, LocalDate expiresAt) {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(WalletEntity.Fields.userId, userId));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        WalletEntity wallet = this.repoFindOne(filter);
        if (wallet == null) {
            wallet = WalletEntity.builder()
                    .userId(userId)
                    .balance(0L)
                    .totalEarned(0L)
                    .totalSpent(0L)
                    .totalExpired(0L)
                    .balanceDetails(new ArrayList<>()).build();
            repository.save(wallet);
        }

        long totalEarned = wallet.getTotalEarned() + amount;
        wallet.setBalance(wallet.getBalance() + amount);
        wallet.setTotalEarned(totalEarned);

        long currentBalance = getBalance(wallet);
        WalletTransactionEntity transaction = walletTransactionService.createTransaction(wallet.getId(), amount, currentBalance + amount, TxnType.CREDIT, source);

        WalletEntity.WalletBalanceDetail balanceDetail = WalletEntity.WalletBalanceDetail.builder()
                .amount(amount)
                .creditedAt(LocalDate.now())
                .expiresAt(expiresAt)
                .sourceTxnId(transaction.getId())
                .build();

        if (wallet.getBalanceDetails() == null) {
            wallet.setBalanceDetails(new ArrayList<>());
        }
        wallet.getBalanceDetails().add(balanceDetail);
        repository.save(wallet);
    }

    public long getBalance(WalletEntity wallet) {
        if (wallet == null || wallet.getBalanceDetails() == null) {
            return 0L;
        }

        return wallet.getBalanceDetails().stream()
                .filter(detail -> detail.getExpiresAt() == null || detail.getExpiresAt().isAfter(LocalDate.now()))
                .mapToLong(WalletEntity.WalletBalanceDetail::getAmount)
                .sum();
    }
}
