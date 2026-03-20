package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.common.enums.TimeSlot;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Builder
public record SecureOrderDetailsBean(
        @JsonProperty("secure_return_id")
        String secureReturnId,
        @JsonProperty("order_id")
        String orderId,
        @JsonProperty("order_code")
        String orderCode,
        String status,
        @JsonProperty("order_date")
        String orderDate,
        @JsonProperty("order_amount")
        BigDecimal orderAmount,
        @JsonProperty("order_quantity")
        String orderQuantity,
        @JsonProperty("order_items")
        List<SecureOrderItemDetail> orderItems,
        @JsonProperty("delivery_partner_id")
        String deliveryPartnerId,
        @JsonProperty("time_slot")
        TimeSlot timeSlot,
        @JsonProperty("scheduled_return_date")
        LocalDate scheduledReturnDate,
        @JsonProperty("max_expected_secure_refund")
        BigDecimal maxExpectedSecureRefund,
        @JsonProperty("total_selling_price_after_discount")
        BigDecimal totalSellingPriceAfterDiscount,
        @JsonProperty("refund_status")
        String refundStatus,
        @JsonProperty("refund_transaction_id")
        String refundTransactionId,
        @JsonProperty("is_eligible_for_reschedule")
        boolean eligibleForReschedule,
        @JsonProperty("max_reschedule_count")
        int maxRescheduleCount,
        @JsonProperty("reschedule_count")
        int rescheduleCount,
        @JsonProperty("refund_amount")
        BigDecimal refundAmount,
        @JsonProperty("refund_date")
        LocalDateTime refundDate

) {
}
