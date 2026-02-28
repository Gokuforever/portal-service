package com.sorted.common.entity.mongo;

import com.sorted.common.beans.BuyerInfo;
import com.sorted.common.beans.InvoiceItem;
import com.sorted.common.beans.PaymentInfo;
import com.sorted.common.beans.SellerInfo;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Builder
@Document(collection = "invoice")
public class Invoice extends BaseMongoEntity<String>{

    @Field("order_id")
    private String orderId;
    @Field("order_code")
    private String orderCode;
    @Field("invoice_id")
    private String invoiceId;
    @Field("invoice_date")
    private LocalDateTime invoiceDate;
    private SellerInfo seller;
    private BuyerInfo buyer;
    private List<InvoiceItem> items;
    @Field("total_amount")
    private BigDecimal totalAmount;
    @Field("delivery_charge")
    private BigDecimal deliveryCharge;
    @Field("total_net_amount")
    private BigDecimal totalNetAmount;
    @Field("total_amount_in_words")
    private String totalAmountInWords;
    @Field("payment_info")
    private PaymentInfo paymentInfo;
    @Field("generated_url")
    private String generatedUrl;

}
