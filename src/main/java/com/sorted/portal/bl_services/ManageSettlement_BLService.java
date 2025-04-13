package com.sorted.portal.bl_services;

import com.sorted.commons.beans.FeeResult;
import com.sorted.commons.beans.SettlementDetails;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.File_Upload_Details;
import com.sorted.commons.entity.mongo.Order_Details;
import com.sorted.commons.entity.service.File_Upload_Details_Service;
import com.sorted.commons.entity.service.Order_Details_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.*;
import com.sorted.commons.exceptions.AccessDeniedException;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.Preconditions;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.portal.request.beans.BlankReqBean;
import com.sorted.portal.request.beans.FindSettlementBean;
import com.sorted.portal.request.beans.SettlementReqBean;
import com.sorted.portal.response.beans.FindSettlementResponse;
import com.sorted.portal.response.beans.SettlementAnalyticsResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.sorted.commons.enums.OrderStatus.*;

@Slf4j
@RestController
public class ManageSettlement_BLService {

    private final Users_Service usersService;
    private final File_Upload_Details_Service fileUploadDetailsService;
    private final Order_Details_Service orderDetailsService;
    private final static int fee_percentage = 10;

    public ManageSettlement_BLService(Users_Service usersService,
                                      File_Upload_Details_Service fileUploadDetailsService,
                                      Order_Details_Service orderDetailsService) {
        this.usersService = usersService;
        this.fileUploadDetailsService = fileUploadDetailsService;
        this.orderDetailsService = orderDetailsService;
    }

    @PostMapping("/settlement/analytics")
    public SEResponse analytics(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        log.info("/settlement/find:: API started");
        BlankReqBean req = request.getGenericRequestDataObject(BlankReqBean.class);
        CommonUtils.extractHeaders(httpServletRequest, req);

        UsersBean usersBean = usersService.validateUserForActivity(req, Permission.VIEW, Activity.SETTLEMENT);
        if (usersBean == null) {
            throw new AccessDeniedException();
        }

        AggregationFilter.SEFilter filter = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        switch (usersBean.getRole().getUser_type()) {
            case SELLER:
                filter.addClause(AggregationFilter.WhereClause.eq(Order_Details.Fields.seller_id, usersBean.getSeller().getId()));
            case SUPER_ADMIN:
                break;
            default:
                throw new AccessDeniedException();
        }
        filter.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(AggregationFilter.WhereClause.in(Order_Details.Fields.status_id, List.of(DELIVERED.getId(), OUT_FOR_DELIVERY.getId(),
                READY_FOR_PICK_UP.getId(), RIDER_ASSIGNED.getId(), ORDER_ACCEPTED.getId())));

        List<Order_Details> orderDetails = orderDetailsService.repoFind(filter);
        if (CollectionUtils.isEmpty(orderDetails)) {
            return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
        }

        // Split orders into paid and unpaid with a single pass
        Map<Boolean, List<Order_Details>> ordersByPayoutStatus = orderDetails.stream()
                .collect(Collectors.partitioningBy(order -> Boolean.TRUE.equals(order.getIs_payout_done())));

        List<Order_Details> paidOrders = ordersByPayoutStatus.get(true);
        List<Order_Details> unpaidOrders = ordersByPayoutStatus.get(false);

        // Calculate earning and pendingRevenue directly with sum operations
        BigDecimal paid = paidOrders.stream()
                .map(order -> order.getSettlement_details().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unpaid = unpaidOrders.stream()
                .map(order -> CommonUtils.calculateFees(order.getTotal_amount(), 10).cost())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        SettlementAnalyticsResponse response = new SettlementAnalyticsResponse(paid, unpaid);
        return SEResponse.getBasicSuccessResponseObject(response, ResponseCode.SUCCESSFUL);
    }

    @PostMapping("/settlement/find")
    public SEResponse find(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        log.info("/settlement/find:: API started");
        FindSettlementBean req = request.getGenericRequestDataObject(FindSettlementBean.class);
        CommonUtils.extractHeaders(httpServletRequest, req);

        UsersBean usersBean = usersService.validateUserForActivity(req, Permission.VIEW, Activity.SETTLEMENT);
        if (usersBean == null) {
            throw new AccessDeniedException();
        }

        AggregationFilter.SEFilter filter = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        switch (usersBean.getRole().getUser_type()) {
            case SELLER:
                filter.addClause(AggregationFilter.WhereClause.eq(Order_Details.Fields.seller_id, usersBean.getSeller().getId()));
            case SUPER_ADMIN:
                break;
            default:
                throw new AccessDeniedException();
        }

        if (StringUtils.isNoneBlank(req.getOrder_id())) {
            filter.addClause(AggregationFilter.WhereClause.like(Order_Details.Fields.code, req.getOrder_id()));
        }
        if (Objects.nonNull(req.getStatus())) {
            filter.addClause(AggregationFilter.WhereClause.eq(Order_Details.Fields.is_payout_done, req.getStatus()));
        }

        filter.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(AggregationFilter.WhereClause.in(Order_Details.Fields.status_id, List.of(DELIVERED.getId(), OUT_FOR_DELIVERY.getId(),
                READY_FOR_PICK_UP.getId(), RIDER_ASSIGNED.getId(), ORDER_ACCEPTED.getId())));

        List<Order_Details> orderDetails = orderDetailsService.repoFind(filter);
        if (CollectionUtils.isEmpty(orderDetails)) {
            return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
        }

        List<FindSettlementResponse> responseList = orderDetails.stream().map(order ->
                FindSettlementResponse.builder()
                        .orderId(order.getId())
                        .orderCode(order.getCode())
                        .amount(CommonUtils.paiseToRupee(order.getTotal_amount()))
                        .feeAndCost(CommonUtils.calculateFees(order.getTotal_amount(), fee_percentage))
                        .expectedPayoutDate(order.getOrder_status_history().stream()
                                .filter(orderStatusHistory -> orderStatusHistory.getStatus().equals(ORDER_ACCEPTED))
                                .findFirst().get().getModification_date().plusDays(7).toLocalDate().toString())
                        .actualPayoutDate(Boolean.TRUE.equals(order.getIs_payout_done()) ? order.getSettlement_details().getTxnDate() : null)
                        .status(Boolean.TRUE.equals(order.getIs_payout_done()))
                        .build()
        ).toList();
        return SEResponse.getBasicSuccessResponseList(responseList, ResponseCode.SUCCESSFUL);
    }

    @PostMapping("/settle")
    public SEResponse settle(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        log.info("settlement:: API started");
        SettlementReqBean req = request.getGenericRequestDataObject(SettlementReqBean.class);
        CommonUtils.extractHeaders(httpServletRequest, req);

        UsersBean usersBean = usersService.validateUserForActivity(req, Permission.EDIT, Activity.SETTLEMENT);
        if (usersBean == null) {
            throw new AccessDeniedException();
        }
        if (usersBean.getRole().getUser_type() != UserType.SUPER_ADMIN) {
            throw new AccessDeniedException();
        }

        // Validate Order ID
        Preconditions.check(StringUtils.isNoneBlank(req.getOrderId()), ResponseCode.MISSING_ORDER_ID);

        // Validate Settlement Details
        SettlementDetails settlementDetails = req.getSettlementDetails();
        Preconditions.check(settlementDetails != null, ResponseCode.MISSING_PAYMENT_MODE);

        // Validate Payment Mode
        Preconditions.check(settlementDetails.getPaymentMode() != null, ResponseCode.MISSING_PAYMENT_MODE);

        // Validate Payment Mode specific fields
        switch (settlementDetails.getPaymentMode()) {
            case UPI -> {
                Preconditions.check(StringUtils.isNoneBlank(settlementDetails.getVpa()), ResponseCode.MISSING_VPA);
                Preconditions.check(StringUtils.isNoneBlank(settlementDetails.getTxnId()), ResponseCode.MISSING_TRANSACTION_ID);
            }
            case IMPS, NEFT, RTGS -> {
                Preconditions.check(StringUtils.isNoneBlank(settlementDetails.getAccountNumber()), ResponseCode.MISSING_ACCOUNT_NUMBER);
                Preconditions.check(StringUtils.isNoneBlank(settlementDetails.getIfscCode()), ResponseCode.MISSING_IFSC_CODE);
                Preconditions.check(SERegExpUtils.isIfsc(settlementDetails.getIfscCode()), ResponseCode.INVALID_IFSC_CODE);
                Preconditions.check(StringUtils.isNoneBlank(settlementDetails.getTxnId()), ResponseCode.MISSING_TRANSACTION_ID);
            }
            default ->
                    Preconditions.check(StringUtils.isNoneBlank(settlementDetails.getChequeNumber()), ResponseCode.MISSING_CHEQUE_NUMBER);
        }

        // Validate Common Fields
        Preconditions.check(settlementDetails.getAmount() != null, ResponseCode.INVALID_PAYMENT_AMOUNT);
        Preconditions.check(BigDecimal.ZERO.compareTo(settlementDetails.getAmount()) < 0, ResponseCode.INVALID_PAYMENT_AMOUNT);
        Preconditions.check(StringUtils.isNoneBlank(settlementDetails.getBeneficiaryName()), ResponseCode.MISSING_BENEFICIARY_NAME);
        Preconditions.check(StringUtils.isNoneBlank(settlementDetails.getTxnDate()), ResponseCode.MISSING_TRANSACTION_DATE);

        // Parse and validate transaction date
        LocalDate txnDate = LocalDate.parse(settlementDetails.getTxnDate());
        Preconditions.check(!txnDate.isAfter(LocalDate.now()), ResponseCode.INVALID_TRANSACTION_DATE);

        // Validate transaction screenshot if provided
        if (StringUtils.isNoneBlank(req.getSettlementDetails().getTxnSsId())) {
            AggregationFilter.SEFilter filter = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
            filter.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.id, req.getSettlementDetails().getTxnSsId()));
            filter.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            File_Upload_Details fileUploadDetails = fileUploadDetailsService.repoFindOne(filter);
            if (fileUploadDetails == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.IMAGES_NOT_FOUND);
            }
        }

        // Find order
        AggregationFilter.SEFilter filterOD = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filterOD.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.id, req.getOrderId()));
        filterOD.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Order_Details orderDetails = orderDetailsService.repoFindOne(filterOD);
        if (orderDetails == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.ORDER_NOT_FOUND);
        }

        // Validate order status
        switch (orderDetails.getStatus()) {
            case DELIVERED, OUT_FOR_DELIVERY, READY_FOR_PICK_UP, RIDER_ASSIGNED, ORDER_ACCEPTED:
                break;
            default:
                throw new CustomIllegalArgumentsException(ResponseCode.SETTLEMENT_NOT_ALLOWED);
        }

        // Check if order is already settled
        if (Boolean.TRUE.equals(orderDetails.getIs_payout_done())) {
            throw new CustomIllegalArgumentsException(ResponseCode.ALREADY_SETTLED);
        }

        // Validate settlement amount
        Long costInPaise = CommonUtils.calculateFees(orderDetails.getTotal_amount(), fee_percentage).costInPaise();
        Long settlementAmount = CommonUtils.rupeeToPaise(req.getSettlementDetails().getAmount());
        Preconditions.check(costInPaise.compareTo(settlementAmount) == 0, ResponseCode.AMOUNT_VALIDATION_FAILED);


        SettlementDetails details = new SettlementDetails(settlementDetails);

        // Update order with settlement details
        orderDetails.setIs_payout_done(true);
        orderDetails.setSettlement_details(details);

        orderDetailsService.update(orderDetails.getId(), orderDetails, usersBean.getId());
        log.info("Settlement successful for order ID: {}", req.getOrderId());
        return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
    }

}