package com.sorted.portal.assisting.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CartItemsBean {

    private String product_id;
    private Long quantity;
    private boolean secure_item;
    private boolean add;
}
