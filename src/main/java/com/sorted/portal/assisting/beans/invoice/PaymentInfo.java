package com.sorted.portal.assisting.beans.invoice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class PaymentInfo {
    @JsonProperty("payment_method")
    private String paymentMethod; // PhonePe, UPI, etc.
    @JsonProperty("transaction_id")
    private String transactionId;
    @JsonProperty("payment_date")
    private LocalDateTime paymentDate;
}
