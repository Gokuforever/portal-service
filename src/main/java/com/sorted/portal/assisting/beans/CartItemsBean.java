package com.sorted.portal.assisting.beans;

import lombok.Data;

@Data
public class CartItemsBean {

    private String product_id;
    private Long quantity;
    private boolean secure_item;
    private boolean add;
}
