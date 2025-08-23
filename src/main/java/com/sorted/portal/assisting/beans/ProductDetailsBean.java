package com.sorted.portal.assisting.beans;

import com.sorted.commons.beans.Media;
import com.sorted.commons.beans.SelectedSubCategories;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductDetailsBean {

    private String name;
    private String id;
    private String product_code;
    private BigDecimal mrp;
    private BigDecimal selling_price;
    private String description;
    private int quantity;
    private List<SelectedSubCategories> selected_sub_catagories;
    private List<ProductDetailsBean> varients;
    private List<ProductDetailsBeanList> related_products;
    private List<Media> media;
    private String category_id;
    private String category_name;
    private CartDetails cart_info;
    private Boolean secure;
    private String seller_code;
    private String seller_name;
    private String modification_date;
    private Integer group_id;

    @Builder
    @Data
    public static class CartDetails {
        private long secure_items;
        private long normal_items;
    }
}
