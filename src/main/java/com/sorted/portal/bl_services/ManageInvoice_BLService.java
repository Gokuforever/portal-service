package com.sorted.portal.bl_services;

import com.sorted.commons.beans.Spoc_Details;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.Order_Details_Service;
import com.sorted.commons.entity.service.Order_Item_Service;
import com.sorted.commons.entity.service.Seller_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.Permission;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.Preconditions;
import com.sorted.portal.assisting.beans.invoice.BuyerInfo;
import com.sorted.portal.assisting.beans.invoice.InvoiceDTO;
import com.sorted.portal.assisting.beans.invoice.InvoiceItem;
import com.sorted.portal.assisting.beans.invoice.SellerInfo;
import com.sorted.portal.request.beans.GenerateInvoiceBean;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ManageInvoice_BLService {

    private final Users_Service usersService;
    private final Order_Details_Service orderService;
    private final Seller_Service sellerService;
    private final Order_Item_Service orderItemService;

    @PostMapping
    public void generateInvoice(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        GenerateInvoiceBean req = request.getGenericRequestDataObject(GenerateInvoiceBean.class);
        CommonUtils.extractHeaders(httpServletRequest, req);

        UsersBean usersBean = usersService.validateUserForActivity(req, Permission.VIEW, Activity.ORDER_MANAGEMENT);
        Preconditions.check(usersBean.getRole().getUser_type() == UserType.CUSTOMER, ResponseCode.ACCESS_DENIED);
        Preconditions.check(StringUtils.hasText(req.getOrderId()), ResponseCode.MANDATE_ORDER_ID);

        Order_Details orderDetails = orderService.findById(req.getOrderId()).orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.ORDER_NOT_FOUND));
        switch (orderDetails.getStatus()) {
            case DELIVERED:
                break;
            default:
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_ORDER_STATUS);
        }

        Users buyer = usersService.findById(orderDetails.getUser_id()).orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.USER_NOT_FOUND));
        Seller seller = sellerService.findById(orderDetails.getSeller_id()).orElseThrow(() -> new CustomIllegalArgumentsException(ResponseCode.SELLER_NOT_FOUND));

        SEFilter filterOI = new SEFilter(SEFilterType.AND);
        filterOI.addClause(WhereClause.eq(Order_Item.Fields.order_id, req.getOrderId()));
        filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Order_Item> orderItems = orderItemService.repoFind(filterOI);
        if (!CollectionUtils.isEmpty(orderItems)) {
            for (Order_Item item : orderItems) {
                InvoiceItem.builder()
                        .productId(item.getProduct_code())
                        .productName(item.getProduct_name())
                        .hsnCode("")
                        .quantity(item.getQuantity())
                        .unitPrice(CommonUtils.paiseToRupee(item.getSelling_price()))
                        .totalPrice(CommonUtils.paiseToRupee(item.getTotal_cost()))
                        .build();
            }
        }

        InvoiceDTO.builder()
                .invoiceId("")
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

                .build();

    }
}
