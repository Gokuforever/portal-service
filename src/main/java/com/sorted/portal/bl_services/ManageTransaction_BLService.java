package com.sorted.portal.bl_services;

import com.phonepe.sdk.pg.payments.v2.models.response.StandardCheckoutPayResponse;
import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.beans.PayNowBean;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Order_Details;
import com.sorted.commons.entity.mongo.Order_Dump;
import com.sorted.commons.entity.service.Order_Details_Service;
import com.sorted.commons.entity.service.Order_Dump_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.GsonUtils;
import com.sorted.commons.utils.OrderUtility;
import com.sorted.portal.PhonePe.PhonePeUtility;
import com.sorted.portal.response.beans.AddressResponse;
import com.sorted.portal.response.beans.FindOneOrder;
import com.sorted.portal.response.beans.OrderItemResponse;
import com.sorted.portal.response.beans.PayNowResponse;
import com.sorted.portal.service.order.OrderStatusCheckService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.ws.rs.NotFoundException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ManageTransaction_BLService {

    private final Users_Service users_Service;
    private final Order_Details_Service order_Details_Service;
    private final Order_Dump_Service orderDumpService;
    private final PhonePeUtility phonePeUtility;
    private final OrderStatusCheckService orderStatusCheckService;
    private final OrderUtility orderUtility;

    @Value("${se.fixed-delivery-charge.in-paise:4000}")
    private long fixedDeliveryFee;

    @Value("${se.minimum-cart-value.in-paise:39900}")
    private long minCartValueInPaise;

    @Value("${se.small-cart-fee.in-paise:1000}")
    private long fixedSmallCartFee;

    @Value("${se.handling-fee.in-paise:900}")
    private long fixedHandlingFee;

    @GetMapping("/status")
    public FindOneOrder status(@RequestParam("orderId") String orderId, HttpServletRequest httpServletRequest) {
        if (!StringUtils.hasText(orderId)) {
            log.error("status:: Order ID is empty or null");
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_ORDER_ID);
        }

        String req_user_id = httpServletRequest.getHeader("req_user_id");

        UsersBean usersBean = users_Service.validateUserForActivity(req_user_id, Activity.PURCHASE);
        if (!(Objects.requireNonNull(usersBean.getRole().getUser_type()) == UserType.CUSTOMER)) {
            throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }

        log.info("status:: Checking status for order ID: {}", orderId);

        Order_Details order_Details = findOrderDetails(orderId, usersBean.getId());

        try {
            List<OrderItemResponse> orderItemResponseList = orderStatusCheckService.checkOrderStatus(order_Details);

            // Build the complete response with code field included
            AddressResponse addressResponse = buildAddressResponse(order_Details.getDelivery_address());
            return FindOneOrder.builder()
                    .id(orderId)
                    .code(order_Details.getCode())
                    .paymentStatus(order_Details.getPayment_status())
                    .paymentMode(order_Details.getPayment_mode())
                    .totalAmount(order_Details.getTotal_amount())
                    .status(order_Details.getStatus().getCustomer_status())
                    .transactionId(order_Details.getTransaction_id())
                    .orderItems(orderItemResponseList)
                    .deliveryAddress(addressResponse)
                    .build();
        } catch (NotFoundException e) {
            log.error("status:: Order not found: {}", e.getMessage());
            throw e;
        } catch (CustomIllegalArgumentsException e) {
            log.error("status:: Custom exception: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("status:: Unexpected error occurred for order ID {}: {}", orderId, e.getMessage(), e);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    private Order_Details findOrderDetails(String orderId, String userId) {
        SEFilter filterOD = new SEFilter(SEFilterType.AND);
        filterOD.addClause(WhereClause.eq(Order_Details.Fields.user_id, userId));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, orderId));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Order_Details order_Details = order_Details_Service.repoFindOne(filterOD);
        if (order_Details == null) {
            log.error("status:: Order not found with ID: {}", orderId);
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }
        return order_Details;
    }

    private AddressResponse buildAddressResponse(AddressDTO deliveryAddress) {
        return AddressResponse.builder()
                .addressType(deliveryAddress.getAddress_type())
                .city(deliveryAddress.getCity())
                .pincode(deliveryAddress.getPincode())
                .state(deliveryAddress.getState())
                .street_1(deliveryAddress.getStreet_1())
                .street_2(deliveryAddress.getStreet_2())
                .landmark(deliveryAddress.getLandmark())
                .addressTypeDesc(deliveryAddress.getAddress_type_desc())
                .phone_no(deliveryAddress.getPhone_no())
                .build();
    }

    @PostMapping("/pay")
    public PayNowResponse pay(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        Order_Dump orderDump = new Order_Dump(GsonUtils.getGson().toJson(request.getRequestData()));
        orderDumpService.create(orderDump, this.getClass().getSimpleName());

        try {
            log.info("pay:: API started for request: {}", request);
            PayNowBean req = request.getGenericRequestDataObject(PayNowBean.class);
            CommonUtils.extractHeaders(httpServletRequest, req);

            log.info("pay:: Processing payment request for user: {}, delivery address: {}",
                    req.getReq_user_id(), req.getDelivery_address_id());

            // Validate user
            log.debug("pay:: Starting user validation");
            UsersBean usersBean = validateUserForPayment(req);
            log.info("pay:: User validation successful for user: {}, type: {}",
                    usersBean.getId(), usersBean.getRole().getUser_type());

            Order_Details order = orderUtility.validateAndCreateOrder(req, usersBean);

            // Create payment order
            log.debug("pay:: Creating PhonePe payment order");
            Optional<StandardCheckoutPayResponse> checkoutPayResponseOptional =
                    phonePeUtility.createOrder(order.getId(), order.getTotal_amount());

            if (checkoutPayResponseOptional.isEmpty()) {
                log.error("pay:: Failed to create PhonePe order for order ID: {}, amount: {}",
                        order.getId(), order.getTotal_amount());
                throw new CustomIllegalArgumentsException(ResponseCode.PG_ORDER_GEN_FAILED);
            }

            // Update order with payment details
            StandardCheckoutPayResponse checkoutPayResponse = checkoutPayResponseOptional.get();
            String pgOrderId = checkoutPayResponse.getOrderId();
            log.info("pay:: PhonePe order created successfully. PG Order ID: {}", pgOrderId);

            order.setPg_order_id(pgOrderId);
            order_Details_Service.update(order.getId(), order, usersBean.getId());
            log.debug("pay:: Order updated with PG order ID");

            // Build response
            String redirectUrl = checkoutPayResponse.getRedirectUrl();
            String orderId = order.getId();

            log.info("pay:: Payment process completed successfully. Order ID: {}, PG Order ID: {}",
                    orderId, pgOrderId);

            PayNowResponse payNowResponse = PayNowResponse.builder()
                    .redirectUrl(redirectUrl)
                    .orderId(orderId)
                    .build();

            orderDumpService.markSuccess(orderDump, order.getId(), this.getClass().getSimpleName());
            log.info("pay:: API completed successfully for order: {}", orderId);

            return payNowResponse;

        } catch (CustomIllegalArgumentsException ex) {
            log.error("pay:: Custom exception occurred - Code: {}, Message: {}",
                    ex.getResponseCode().getCode(), ex.getResponseCode().getErrorMessage());
            orderDumpService.markFailed(orderDump, ex.getResponseCode().getErrorMessage(),
                    ex.getResponseCode().getCode(), this.getClass().getSimpleName());
            throw ex;
        } catch (Exception e) {
            log.error("pay:: Unexpected exception occurred: {}", e.getMessage(), e);
            orderDumpService.markFailed(orderDump, e.getMessage(), ResponseCode.ERR_0001.getCode(),
                    this.getClass().getSimpleName());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    // Enhanced validation methods with logging
    private UsersBean validateUserForPayment(PayNowBean req) {
        log.debug("validateUserForPayment:: Validating user: {}", req.getReq_user_id());

        UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.PURCHASE);

        switch (usersBean.getRole().getUser_type()) {
            case CUSTOMER -> {
                log.debug("validateUserForPayment:: Valid customer user type");
            }
            case GUEST -> {
                log.warn("validateUserForPayment:: Guest user attempted payment: {}", req.getReq_user_id());
                throw new CustomIllegalArgumentsException(ResponseCode.PROMT_SIGNUP);
            }
            default -> {
                log.warn("validateUserForPayment:: Invalid user type {} for payment",
                        usersBean.getRole().getUser_type());
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
            }
        }
        return usersBean;
    }
}
