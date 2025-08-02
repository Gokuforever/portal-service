package com.sorted.portal.assisting.beans.invoice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


@Getter
@Setter
@Builder
public class InvoiceDTO {

    @JsonProperty("invoice_id")
    private String invoiceId;
    @JsonProperty("invoice_date")
    private LocalDateTime invoiceDate;
    private SellerInfo seller;
    private BuyerInfo buyer;
    private List<InvoiceItem> items;
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;
    @JsonProperty("total_gst_amount")
    private BigDecimal totalGstAmount;
    @JsonProperty("total_net_amount")
    private BigDecimal totalNetAmount;
    @JsonProperty("total_amount_in_words")
    private String totalAmountInWords;
    @JsonProperty("payment_info")
    private PaymentInfo paymentInfo;

}

