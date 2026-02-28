package com.sorted.common.entity.mongo;


import com.sorted.common.enums.TxnType;
import com.sorted.common.enums.WalletTxnSource;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Builder
@Document("wallet_transaction")
public class WalletTransactionEntity extends BaseMongoEntity<String> {

    @Field("wallet_id")
    private String walletId;
    @Field("user_id")
    private String userId;
    private Long amount;
    private TxnType type;
    private Long balance;
    private WalletTxnSource source;
}
