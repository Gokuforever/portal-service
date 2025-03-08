package com.sorted.portal.response.beans;

import com.sorted.commons.beans.Refund_Details;
import com.sorted.commons.enums.OrderStatus;
import com.sorted.commons.enums.PurchaseType;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public class OrderItemDTO {
    private String id;
    private String product_id;
    private String product_code;
    private Long quantity;
    private Long selling_price;
    private Long total_cost;
    private PurchaseType type;
    private Integer status_id;
    private OrderStatus status;
    private LocalDateTime return_date;
    private Refund_Details refund_details;
}
