package com.sorted.common.utils;

import com.sorted.common.beans.*;
import com.sorted.common.entity.mongo.*;
import com.sorted.common.entity.service.InvoiceService;
import com.sorted.common.entity.service.Order_Item_Service;
import com.sorted.common.entity.service.Seller_Service;
import com.sorted.common.entity.service.Users_Service;
import com.sorted.common.enums.DocumentType;
import com.sorted.common.enums.OrderStatus;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@RequiredArgsConstructor
@Service
public class GenerateInvoiceService {

    private final Users_Service usersService;
    private final Seller_Service sellerService;
    private final Order_Item_Service orderItemService;
    private final GcpStorageService gcpStorageService;
    private final InvoiceService invoiceService;


    public String generateInvoice(Order_Details orderDetails) throws IOException {
        log.info("Starting invoice generation for order ID: {}", orderDetails.getId());

        if (orderDetails.getStatus() != OrderStatus.DELIVERED) {
            log.warn("Invoice generation failed - invalid order status: {} for order ID: {}",
                    orderDetails.getStatus(), orderDetails.getId());
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_ORDER_STATUS);
        }

        // Check if invoice already exists
        log.debug("Checking if invoice already exists for order ID: {}", orderDetails.getId());
        SEFilter filterI = new SEFilter(SEFilterType.AND);
        filterI.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, orderDetails.getId()));
        filterI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        Invoice invoice = invoiceService.repoFindOne(filterI);
        if (invoice != null) {
            log.info("Invoice already exists for order ID: {}, returning existing URL", orderDetails.getId());
            return invoice.getGeneratedUrl();
        }

        // Validate and get buyer information
        log.debug("Validating and fetching buyer information for user ID: {}", orderDetails.getUser_id());
        UsersBean buyer = usersService.validateAndGetUserInfo(orderDetails.getUser_id());
        log.debug("Successfully retrieved buyer information for user ID: {}", orderDetails.getUser_id());

        // Validate and get seller information
        log.debug("Fetching seller information for seller ID: {}", orderDetails.getSeller_id());
        Seller seller = sellerService.findById(orderDetails.getSeller_id())
                .orElseThrow(() -> {
                    log.error("Seller not found for seller ID: {}", orderDetails.getSeller_id());
                    return new CustomIllegalArgumentsException(ResponseCode.SELLER_NOT_FOUND);
                });
        log.debug("Successfully retrieved seller information for seller ID: {}", orderDetails.getSeller_id());

        // Fetch order items
        log.debug("Fetching order items for order ID: {}", orderDetails.getId());
        List<InvoiceItem> invoiceItems = new ArrayList<>();
        SEFilter filterOI = new SEFilter(SEFilterType.AND);
        filterOI.addClause(WhereClause.eq(Order_Item.Fields.order_id, orderDetails.getId()));
        filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Order_Item> orderItems = orderItemService.repoFind(filterOI);

        if (!CollectionUtils.isEmpty(orderItems)) {
            log.debug("Found {} order items for order ID: {}", orderItems.size(), orderDetails.getId());

            Map<String, List<Order_Item>> comboItemMap =
                    orderItems.stream().filter(Order_Item::isCombo).collect(Collectors.groupingBy(Order_Item::getCombo_id));

            if (!CollectionUtils.isEmpty(comboItemMap)) {
                for (Map.Entry<String, List<Order_Item>> entry : comboItemMap.entrySet()) {
                    Order_Item item = entry.getValue().get(0);
                    invoiceItems.add(InvoiceItem.builder()
                            .productId(item.getCombo_code())
                            .productName(item.getCombo_name())
                            .hsnCode("")
                            .quantity(item.getQuantity())
                            .unitPrice(CommonUtils.paiseToRupee(item.getCombo_selling_price()))
                            .totalPrice(CommonUtils.paiseToRupee(item.getCombo_mrp()))
                            .build());
                }
            }
            List<Order_Item> order_items = orderItems.stream().filter(item -> !item.isCombo()).toList();
            for (Order_Item item : order_items) {
                invoiceItems.add(InvoiceItem.builder()
                        .productId(item.getProduct_code())
                        .productName(item.getProduct_name())
                        .hsnCode("")
                        .quantity(item.getQuantity())
                        .unitPrice(CommonUtils.paiseToRupee(item.getSelling_price()))
                        .totalPrice(CommonUtils.paiseToRupee(item.getTotal_cost()))
                        .build());
            }
        } else {
            log.warn("No order items found for order ID: {}", orderDetails.getId());
        }

        invoice = Invoice.builder()
                .orderCode(orderDetails.getCode())
                .invoiceDate(LocalDateTime.now())
                .seller(SellerInfo.builder()
                        .name("Studeaze Partner Store" + " #" + seller.getStore_no())
                        .address("Nashik - 422001")
                        .gstNo(seller.getGstin())
                        .build())
                .buyer(BuyerInfo.builder()
                        .email(buyer.getEmail_id())
                        .name(StringUtils.isNotNullOrEmpty(buyer.getFirst_name()) ? buyer.getFirst_name() + " " + buyer.getLast_name() : "")
                        .address(orderDetails.getDelivery_address().getFullAddress())
                        .build())
                .items(invoiceItems)
                .totalAmount(CommonUtils.paiseToRupee(orderDetails.getTotal_amount()))
                .deliveryCharge(CommonUtils.paiseToRupee(orderDetails.getEstimated_delivery_charges() == null ? 0L : orderDetails.getEstimated_delivery_charges()))
                .totalNetAmount(CommonUtils.paiseToRupee(orderDetails.getTotal_amount()))
                .totalAmountInWords(IndianCurrencyConverter.convertToWords(CommonUtils.paiseToRupee(orderDetails.getTotal_amount()).doubleValue()))
                .paymentInfo(PaymentInfo.builder()
                        .paymentMethod(orderDetails.getPayment_mode())
                        .transactionId(orderDetails.getTransaction_id())
                        .paymentDate(orderDetails.getOrder_status_history().stream()
                                .filter(e -> e.getStatus() == OrderStatus.TRANSACTION_PROCESSED)
                                .findFirst()
                                .map(Order_Status_History::getModification_date)
                                .orElseThrow(() -> {
                                    log.error("Payment date not found for order ID: {}", orderDetails.getId());
                                    return new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
                                }))
                        .build())
                .build();

        invoice = invoiceService.create(invoice, buyer.getId());

        // Generate PDF
        byte[] pdfBytes = InvoicePdfGenerator.generateInvoicePdf(invoice);

        // Upload to S3
        File_Upload_Details fileUploadDetails = gcpStorageService.uploadPdf(pdfBytes, invoice.getInvoiceId() + ".pdf", buyer, DocumentType.INVOICE);

        // Save invoice to database
        invoice.setGeneratedUrl(fileUploadDetails.getFile_url());
        invoiceService.update(invoice.getId(), invoice, buyer.getId());

        return fileUploadDetails.getFile_url();
    }
}