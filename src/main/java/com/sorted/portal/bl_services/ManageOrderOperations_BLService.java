package com.sorted.portal.bl_services;

import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Order_Details;
import com.sorted.commons.entity.mongo.Order_Item;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.CouponService;
import com.sorted.commons.entity.service.Order_Details_Service;
import com.sorted.commons.entity.service.Order_Item_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.OrderStatus;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.CouponUtility;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.commons.utils.Preconditions;
import com.sorted.portal.request.beans.CompleteRefundBean;
import com.sorted.portal.response.beans.OrderItemsForOperations;
import com.sorted.portal.response.beans.OrdersForOperationsBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.scheduling.annotation.Async;
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
    private final Order_Item_Service orderItemService;
    private final Users_Service usersService;
    private final PorterUtility porterUtility;
    private final CouponService couponService;
    private final CouponUtility couponUtility;

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
            case ORDER_CANCELLED -> {
                porterUtility.cancelOrder(order, request.userName());
            }
            default -> throw new CustomIllegalArgumentsException(ResponseCode.INVALID_ORDER_STATUS);
        }
    }

    @PostMapping("/reattempt-order")
    public void reattemptOrder(@RequestBody CompleteRefundBean request) {
        Preconditions.check(StringUtils.hasText(request.orderId()), ResponseCode.MISSING_ORDER_ID);
        Optional<Order_Details> optionalOrderDetails = orderDetailsService.findById(request.orderId());
        Preconditions.check(optionalOrderDetails.isPresent(), ResponseCode.ORDER_NOT_FOUND);
        Order_Details order = optionalOrderDetails.get();
        Preconditions.check(order.getStatus().equals(OrderStatus.DELIVERY_FAILED) || order.getStatus().equals(OrderStatus.ORDER_CANCELLED), ResponseCode.ORDER_NOT_FOUND);
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Order_Item.Fields.order_id, request.orderId()));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Order_Item> orderItems = orderItemService.repoFind(filter);
        Preconditions.check(!CollectionUtils.isEmpty(orderItems), ResponseCode.ORDER_NOT_FOUND);

        order.setId(null);
        order.setDp_order_id(null);
        order.setOrder_status_history(null);
        order.setStatus(OrderStatus.ORDER_PLACED, request.userName());
        order.setStatus(OrderStatus.TRANSACTION_PROCESSED, request.userName());
        order.setStatus(OrderStatus.ORDER_ACCEPTED, request.userName());

        Order_Details orderDetails = orderDetailsService.create(order, request.userName());

        orderItems.forEach(e -> createOrderItem(request, e, orderDetails));
    }

    @Async
    private void createOrderItem(CompleteRefundBean request, Order_Item orderItem, Order_Details orderDetails) {
        orderItem.setId(null);
        orderItem.setStatus_history(null);
        orderItem.setOrder_id(orderDetails.getId());
        orderItem.setStatus(OrderStatus.ORDER_PLACED, request.userName());
        orderItem.setStatus(OrderStatus.TRANSACTION_PROCESSED, request.userName());
        orderItem.setStatus(OrderStatus.ORDER_ACCEPTED, request.userName());
        orderItemService.create(orderItem, request.userName());
    }

    @GetMapping("/fetch/orders-in-transit")
    public List<OrdersForOperationsBean> fetchOrdersInTransit() {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.in(Order_Details.Fields.status_id, List.of(OrderStatus.OUT_FOR_DELIVERY.getId(), OrderStatus.READY_FOR_PICK_UP.getId(), OrderStatus.RIDER_ASSIGNED.getId(), OrderStatus.DELIVERY_FAILED.getId())));
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


    @GetMapping("/fetch/orders-for-reattempt")
    public List<OrdersForOperationsBean> fetchOrdersForReattempt() {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.in(Order_Details.Fields.status_id, List.of(OrderStatus.OUT_FOR_DELIVERY.getId(), OrderStatus.READY_FOR_PICK_UP.getId(), OrderStatus.RIDER_ASSIGNED.getId())));
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

    @GetMapping("/fetch/orders")
    public List<OrdersForOperationsBean> fetchOrders() {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.nin(Order_Details.Fields.status_id, List.of(OrderStatus.TRANSACTION_FAILED.getId())));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        AggregationFilter.OrderBy orderBy = new AggregationFilter.OrderBy(BaseMongoEntity.Fields.modification_date, AggregationFilter.SortOrder.DESC);
        filter.setOrderBy(orderBy);

        List<Order_Details> orderDetails = orderDetailsService.repoFind(filter);
        if (CollectionUtils.isEmpty(orderDetails)) {
            return null;
        }

        SEFilter filterOI = new SEFilter(SEFilterType.AND);
        filterOI.addClause(WhereClause.in(Order_Item.Fields.order_id, orderDetails.stream().map(Order_Details::getId).toList()));
        filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Order_Item> orderItems = orderItemService.repoFind(filterOI);

        Map<String, List<Order_Item>> orderItemMap = orderItems.stream().collect(Collectors.groupingBy(Order_Item::getOrder_id));

        Map<String, Users> usersMap = new HashMap<>();

        SEFilter filterU = new SEFilter(SEFilterType.AND);
        filterU.addClause(WhereClause.in(BaseMongoEntity.Fields.id, orderDetails.stream().map(Order_Details::getUser_id).toList()));
        filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Users> users = usersService.repoFind(filterU);
        if (!CollectionUtils.isEmpty(users)) {
            usersMap.putAll(users.stream().collect(Collectors.toMap(Users::getId, Function.identity())));
        }

        return orderDetails.stream().map(e -> mapToBean(e, usersMap, orderItemMap)).toList();
    }

    private OrdersForOperationsBean mapToBean(Order_Details order, Map<String, Users> usersMap, Map<String, List<Order_Item>> orderItemMap) {
        return OrdersForOperationsBean.builder()
                .orderId(order.getId())
                .orderCode(order.getCode())
                .rejectedReason(order.getRejection_remarks())
                .customerName(usersMap.containsKey(order.getUser_id()) ? usersMap.get(order.getUser_id()).getFirst_name() + " " + usersMap.get(order.getUser_id()).getLast_name() : "")
                .customerEmail(usersMap.containsKey(order.getUser_id()) ? usersMap.get(order.getUser_id()).getEmail_id() : "")
                .userPhoneNo(usersMap.containsKey(order.getUser_id()) ? usersMap.get(order.getUser_id()).getMobile_no() : "")
                .nameForDelivery(order.getDelivery_address().getFirst_name() + " " + order.getDelivery_address().getLast_name())
                .phoneForDelivery(order.getDelivery_address().getPhone_no())
                .customerAddress(order.getDelivery_address().getFullAddress())
                .status(order.getStatus().name())
                .statusId(order.getStatus_id())
                .totalAmount(CommonUtils.paiseToRupee(order.getTotal_amount()))
                .deliveryCharge(CommonUtils.paiseToRupee(order.getDelivery_charges()).add(CommonUtils.paiseToRupee(order.getHandling_charges())).add(CommonUtils.paiseToRupee(order.getSmall_cart_fee())))
                .totalItemCost(CommonUtils.paiseToRupee(order.getTotal_items_cost()))
                .totalDiscount(CommonUtils.paiseToRupee(order.getTotal_discount()))
//                .totalMrp(CommonUtils.paiseToRupee(order.getTotal_mrp()))
//                .totalSellingPrice(CommonUtils.paiseToRupee(order.getTotal_selling_price()))
                .orderPlacedAt(order.getCreation_date_str())
                .deliveredAt(order.getCreation_date_str())
                .transactionId(order.getTransaction_id())
                .couponCode(order.getCoupon_code())
                .deliveryPartnerOrderId(order.getDp_order_id())
                .totalItemCount(orderItemMap.containsKey(order.getId()) ? orderItemMap.get(order.getId()).size() : 0)
                .orderItems(orderItemMap.containsKey(order.getId()) ? orderItemMap.get(order.getId()).stream().map(e -> orderItemBeanMapping(e, usersMap)).toList() : null)
                .build();
    }

    private OrderItemsForOperations orderItemBeanMapping(Order_Item e, Map<String, Users> usersMap) {
        return OrderItemsForOperations.builder()
                .orderItemId(e.getId())
                .productId(e.getProduct_id())
                .productName(e.getProduct_name())
                .productImage(e.getCdn_url())
                .quantity(e.getQuantity().intValue())
                .sellingPrice(CommonUtils.paiseToRupee(e.getSelling_price()))
                .totalCost(CommonUtils.paiseToRupee(e.getTotal_cost()))
                .build();
    }


    private static OrdersForOperationsBean rejectedOrderBeanMapping(Order_Details order, Map<String, Users> usersMap) {
        return OrdersForOperationsBean.builder()
                .orderId(order.getId())
                .orderCode(order.getCode())
                .totalAmount(CommonUtils.paiseToRupee(order.getTotal_amount()))
                .rejectedReason(order.getRejection_remarks())
                .customerName(usersMap.containsKey(order.getUser_id()) ? usersMap.get(order.getUser_id()).getFirst_name() + " " + usersMap.get(order.getUser_id()).getLast_name() : "")
                .customerEmail(usersMap.containsKey(order.getUser_id()) ? usersMap.get(order.getUser_id()).getEmail_id() : "")
                .userPhoneNo(usersMap.containsKey(order.getUser_id()) ? "91" + usersMap.get(order.getUser_id()).getMobile_no() : "")
                .nameForDelivery(order.getDelivery_address().getFirst_name() + " " + order.getDelivery_address().getLast_name())
                .phoneForDelivery(order.getDelivery_address().getPhone_no())
                .customerAddress(order.getDelivery_address().getFullAddress())
                .status(order.getStatus().name())
                .statusId(order.getStatus_id())
                .build();
    }

}
