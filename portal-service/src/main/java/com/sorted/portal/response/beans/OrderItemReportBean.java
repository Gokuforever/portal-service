package com.sorted.portal.response.beans;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Builder
@Getter
public class OrderItemReportBean {
    private String name;
    private int quantity;
    private BigDecimal price;
    private BigDecimal total;
}
