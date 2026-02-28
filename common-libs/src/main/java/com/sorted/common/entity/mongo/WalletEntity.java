package com.sorted.common.entity.mongo;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDate;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Builder
@Document("wallet")
public class WalletEntity extends BaseMongoEntity<String> {

    @Field("user_id")
    private String userId;

    @Field("balance_details")
    private List<WalletBalanceDetail> balanceDetails;

    private Long balance;

    @Field("total_earned")
    private Long totalEarned;

    @Field("total_spent")
    private Long totalSpent;

    @Field("total_expired")
    private Long totalExpired;

    @Data
    @Builder
    public static class WalletBalanceDetail {

        @Field("amount")
        private Long amount;

        @Field("credited_at")
        private LocalDate creditedAt;

        @Field("expires_at")
        private LocalDate expiresAt;

        @Field("source_txn_id")
        private String sourceTxnId;
    }

}
