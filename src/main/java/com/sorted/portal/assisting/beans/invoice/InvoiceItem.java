package com.sorted.portal.assisting.beans.invoice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
public class InvoiceItem {
    @JsonProperty("product_id")
    private String productId;
    @JsonProperty("product_name")
    private String productName;
    @JsonProperty("hsn_code")
    private String hsnCode;
    private long quantity;
    @JsonProperty("unit_price")
    private BigDecimal unitPrice;
    @JsonProperty("total_price")
    private BigDecimal totalPrice; // unitPrice * quantity
    @JsonProperty("gst_rate")
    private BigDecimal gstRate; // e.g., 5%, 12%
    @JsonProperty("gst_amount")
    private BigDecimal gstAmount;
}
