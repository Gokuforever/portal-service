package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class PaymentInfo {
    @JsonProperty("payment_method")
    @Field("payment_method")
    private String paymentMethod; // PhonePe, UPI, etc.
    @JsonProperty("transaction_id")
    @Field("transaction_id")
    private String transactionId;
    @JsonProperty("payment_date")
    @Field("payment_date")
    private LocalDateTime paymentDate;
}
