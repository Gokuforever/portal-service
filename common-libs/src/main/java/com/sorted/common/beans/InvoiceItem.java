package com.sorted.common.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class InvoiceItem {
    @JsonProperty("product_id")
    @Field("product_id")
    private String productId;
    @JsonProperty("product_name")
    @Field("product_name")
    private String productName;
    @JsonProperty("hsn_code")
    @Field("hsn_code")
    private String hsnCode;
    private long quantity;
    @JsonProperty("unit_price")
    @Field("unit_price")
    private BigDecimal unitPrice;
    @JsonProperty("total_price")
    @Field("total_price")
    private BigDecimal totalPrice; // unitPrice * quantity
    @JsonProperty("gst_rate")
    @Field("gst_rate")
    private BigDecimal gstRate; // e.g., 5%, 12%
    @JsonProperty("gst_amount")
    @Field("gst_amount")
    private BigDecimal gstAmount;
}
