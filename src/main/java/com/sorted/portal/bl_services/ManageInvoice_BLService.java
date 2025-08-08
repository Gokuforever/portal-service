package com.sorted.portal.bl_services;

import com.sorted.commons.beans.*;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.*;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.IndianCurrencyConverter;
import com.sorted.commons.utils.Preconditions;
import com.sorted.portal.request.beans.GenerateInvoiceBean;
import com.sorted.portal.utils.InvoicePdfGenerator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Log4j2
@RestController
@RequiredArgsConstructor
public class ManageInvoice_BLService {

    private final Users_Service usersService;
    private final Order_Details_Service orderService;
    private final Seller_Service sellerService;
    private final Order_Item_Service orderItemService;
    private final InvoiceService invoiceService;

    @PostMapping("/generateInvoice")
    public String generateInvoice(@RequestBody SERequest request, HttpServletRequest httpServletRequest) throws IOException {
        GenerateInvoiceBean req = request.getGenericRequestDataObject(GenerateInvoiceBean.class);
        CommonUtils.extractHeaders(httpServletRequest, req);

        UsersBean usersBean = usersService.validateUserForActivity(req, Permission.VIEW, Activity.ORDER_MANAGEMENT);
        Preconditions.check(usersBean.getRole().getUser_type() == UserType.CUSTOMER, ResponseCode.ACCESS_DENIED);
        Preconditions.check(StringUtils.hasText(req.getOrderId()), ResponseCode.MANDATE_ORDER_ID);

        Order_Details orderDetails = orderService.findById(req.getOrderId()).orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.ORDER_NOT_FOUND));
        switch (orderDetails.getStatus()) {
            case DELIVERED, READY_FOR_PICK_UP:
                break;
            default:
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_ORDER_STATUS);
        }

        Users buyer = usersService.findById(orderDetails.getUser_id()).orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.USER_NOT_FOUND));
        Seller seller = sellerService.findById(orderDetails.getSeller_id()).orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.SELLER_NOT_FOUND));

        List<InvoiceItem> invoiceItems = new ArrayList<>();
        SEFilter filterOI = new SEFilter(SEFilterType.AND);
        filterOI.addClause(WhereClause.eq(Order_Item.Fields.order_id, req.getOrderId()));
        filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Order_Item> orderItems = orderItemService.repoFind(filterOI);
        if (!CollectionUtils.isEmpty(orderItems)) {
            for (Order_Item item : orderItems) {
                invoiceItems.add(InvoiceItem.builder()
                        .productId(item.getProduct_code())
                        .productName(item.getProduct_name())
                        .hsnCode("")
                        .quantity(item.getQuantity())
                        .unitPrice(CommonUtils.paiseToRupee(item.getSelling_price()))
                        .totalPrice(CommonUtils.paiseToRupee(item.getTotal_cost()))
                        .build());
            }
        }


        Invoice invoice = Invoice.builder()
                .invoiceId("INV" + orderDetails.getCode() + CommonUtils.generateFixedLengthRandomNumber(2))
                .invoiceDate(LocalDateTime.now())
                .seller(SellerInfo.builder()
                        .name(seller.getBusiness_name())
                        .address(orderDetails.getPickup_address().getFullAddress())
                        .phoneNo(seller.getSpoc_details().stream().filter(Spoc_Details::isPrimary).findFirst().get().getMobile_no())
                        .sellerId(seller.getCode())
                        .gstNo(seller.getGstin())
                        .build())
                .buyer(BuyerInfo.builder()
                        .email(buyer.getEmail_id())
                        .name(buyer.getFirst_name() + " " + buyer.getLast_name())
                        .address(orderDetails.getDelivery_address().getFullAddress())
                        .build())
                .items(invoiceItems)
                .totalAmount(CommonUtils.paiseToRupee(orderDetails.getTotal_amount()))
                .totalGstAmount(BigDecimal.ZERO)
                .totalNetAmount(CommonUtils.paiseToRupee(orderDetails.getTotal_amount()))
                .totalAmountInWords(IndianCurrencyConverter.convertToWords(CommonUtils.paiseToRupee(orderDetails.getTotal_amount()).doubleValue()))
                .paymentInfo(PaymentInfo.builder()
                        .paymentMethod(orderDetails.getPayment_mode())
                        .transactionId(orderDetails.getTransaction_id())
                        .paymentDate(orderDetails.getOrder_status_history().stream()
                                .filter(e -> e.getStatus() == OrderStatus.TRANSACTION_PROCESSED)
                                .findFirst()
                                .map(Order_Status_History::getModification_date)
                                .orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.ERR_0001)))
                        .build())
                .build();

        invoice = invoiceService.create(invoice, usersBean.getId());

        String invoiceHtml = InvoicePdfGenerator.generateOnlyHtml(invoice);
        log.info("Invoice HTML: {}", invoiceHtml);
        byte[] pdfBytes = InvoicePdfGenerator.generateInvoicePdf(invoice);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        // Use "inline" to display in browser, "attachment" to download
        headers.setContentDisposition(ContentDisposition.inline().filename("invoice-" + orderDetails.getCode() + ".pdf").build());


        return invoiceHtml;

    }

    public static void main(String[] args) {
        // For amount in words
        String amountInWords = IndianCurrencyConverter.convertToWords(12000.10);
        System.out.println(amountInWords);
    }
}
