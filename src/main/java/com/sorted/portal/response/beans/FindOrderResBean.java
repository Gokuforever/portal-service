package com.sorted.portal.response.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sorted.commons.enums.WeekDay;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record FindOrderResBean(
        String id,
        String code,
        BigDecimal total_amount,
        @JsonProperty("total_selling_price")
        BigDecimal totalSellingPrice,
        @JsonProperty("delivery_charge")
        BigDecimal deliveryFee,
        @JsonProperty("small_cart_fee")
        BigDecimal smallCartFee,
        @JsonProperty("handling_fee")
        BigDecimal handlingFee,
        String status,
        List<WeekDay> non_operational_days,
        List<OrderItemDTO> orderItems,
        int max_return_days,
        String creation_date_str,
        String delivery_partner_id
) {

}
