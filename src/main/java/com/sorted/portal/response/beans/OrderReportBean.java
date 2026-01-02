package com.sorted.portal.response.beans;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class OrderReportBean {

    private String orderId;
    private LocalDateTime orderDate;
    private BigDecimal orderAmount;
    private int orderQuantity;
    private List<OrderItemReportBean> orderItems;

}
