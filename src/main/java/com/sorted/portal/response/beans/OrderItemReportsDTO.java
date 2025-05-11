package com.sorted.portal.response.beans;

import com.sorted.commons.entity.mongo.Order_Item;
import lombok.Getter;

@Getter
public class OrderItemReportsDTO {

    private String order_code;
    private String product_code;
    private Long quantity;
    private String status;

    public OrderItemReportsDTO(Order_Item orderItem) {
        this.order_code = orderItem.getOrder_code();
        this.product_code = orderItem.getProduct_code();
        this.quantity = orderItem.getQuantity();
        this.status = orderItem.getStatus().getInternal_status();
    }

}
