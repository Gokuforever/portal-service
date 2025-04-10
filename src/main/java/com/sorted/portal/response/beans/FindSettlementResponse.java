package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.beans.FeeResult;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public class FindSettlementResponse {
    @JsonProperty("order_id")
    private String orderId;
    @JsonProperty("order_code")
    private String orderCode;
    private BigDecimal amount;
    @JsonProperty("fee_and_cost")
    private FeeResult feeAndCost;
    @JsonProperty("expected_payout_date")
    private String expectedPayoutDate;
    @JsonProperty("actual_payout_date")
    private String actualPayoutDate;
    private boolean status;
}
