package com.sorted.portal.bl_services;

import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Order_Details;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Order_Details_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.OrderStatus;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.Preconditions;
import com.sorted.portal.request.beans.CompleteRefundBean;
import com.sorted.portal.response.beans.OrdersForOperationsBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@Log4j2
@RequiredArgsConstructor
public class ManageOrderOperations_BLService {

    private final Order_Details_Service orderDetailsService;
    private final Users_Service usersService;

    @GetMapping("/fetch/rejected-orders")
    public List<OrdersForOperationsBean> fetchRejectedOrders() {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Order_Details.Fields.status_id, OrderStatus.ORDER_REJECTED.getId()));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Order_Details> orderDetails = orderDetailsService.repoFind(filter);
        if (CollectionUtils.isEmpty(orderDetails)) {
            return null;
        }

        Map<String, Users> usersMap = new HashMap<>();

        SEFilter filterU = new SEFilter(SEFilterType.AND);
        filterU.addClause(WhereClause.in(BaseMongoEntity.Fields.id, orderDetails.stream().map(Order_Details::getUser_id).toList()));
        filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Users> users = usersService.repoFind(filterU);
        if (!CollectionUtils.isEmpty(users)) {
            usersMap.putAll(users.stream().collect(Collectors.toMap(Users::getId, Function.identity())));
        }

        return orderDetails.stream().map(e -> rejectedOrderBeanMapping(e, usersMap)).toList();
    }

    @PostMapping("/update/order-status")
    public void updateOrderStatus(@RequestBody CompleteRefundBean request) {
        Preconditions.check(StringUtils.hasText(request.orderId()), ResponseCode.MISSING_ORDER_ID);
        Preconditions.check(request.statusId() > 0, ResponseCode.INVALID_ORDER_STATUS);
        OrderStatus status = OrderStatus.getById(request.statusId());
        Preconditions.check(status != null, ResponseCode.INVALID_ORDER_STATUS);
        Optional<Order_Details> optionalOrderDetails = orderDetailsService.findById(request.orderId());
        Preconditions.check(optionalOrderDetails.isPresent(), ResponseCode.ORDER_NOT_FOUND);
        Order_Details order = optionalOrderDetails.get();
        switch (status) {
            case FULLY_REFUNDED -> {
                Preconditions.check(order.getStatus().equals(OrderStatus.REFUND_REQUESTED), ResponseCode.INVALID_ORDER_STATUS);
                order.setStatus(OrderStatus.REFUND_REQUESTED, request.userName());
                order.setRefund_transaction_id(request.refundId());
                orderDetailsService.update(order.getId(), order, request.userName());

                // TODO: send email for refund completion
            }
            default -> throw new CustomIllegalArgumentsException(ResponseCode.INVALID_ORDER_STATUS);
        }
    }

    @GetMapping("/fetch/orders-for-reattempt")
    public List<OrdersForOperationsBean> fetchOrdersForReattempt() {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.in(Order_Details.Fields.status_id, List.of(OrderStatus.OUT_FOR_DELIVERY.getId(), OrderStatus.READY_FOR_PICK_UP.getId(), OrderStatus.RIDER_ASSIGNED.getId())));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Order_Details> orderDetails = orderDetailsService.repoFind(filter);
        if (CollectionUtils.isEmpty(orderDetails)) {
            return null;
        }
        return null;
    }


    private static OrdersForOperationsBean rejectedOrderBeanMapping(Order_Details order, Map<String, Users> usersMap) {
        return OrdersForOperationsBean.builder()
                .orderId(order.getId())
                .orderCode(order.getCode())
                .amount(CommonUtils.paiseToRupee(order.getTotal_amount()))
                .reason(order.getRejection_remarks())
                .customerName(usersMap.containsKey(order.getUser_id()) ? usersMap.get(order.getUser_id()).getFirst_name() + " " + usersMap.get(order.getUser_id()).getLast_name() : "")
                .customerEmail(usersMap.containsKey(order.getUser_id()) ? usersMap.get(order.getUser_id()).getEmail_id() : "")
                .userPhoneNo(usersMap.containsKey(order.getUser_id()) ? usersMap.get(order.getUser_id()).getMobile_no() : "")
                .nameForDelivery(order.getDelivery_address().getFirst_name() + " " + order.getDelivery_address().getLast_name())
                .phoneForDelivery(order.getDelivery_address().getPhone_no())
                .customerAddress(order.getDelivery_address().getFullAddress())
                .status(order.getStatus().name())
                .statusId(order.getStatus_id())
                .build();
    }
}
