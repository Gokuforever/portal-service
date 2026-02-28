package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Getter
public enum OrderStatus {

    //@formatter:off
	

	
	ORDER_PLACED(0, "Order Placed", "Order Placed"),
	TRANSACTION_PROCESSED(1, "Payment Success", "Payment Success"),
	ORDER_ACCEPTED(2, "Order Confirmed", "Seller Accepted"),
	READY_FOR_PICK_UP(3, "Ready For Pick Up", "Ready For Pick Up"), // SELLER - Ready for pick up
	RIDER_ASSIGNED(4, "Delivery Partner Assigned", "Delivery Partner Assigned"), // Delivery partner accepts the order
	OUT_FOR_DELIVERY(5, "Out For Delivery", "Out For Delivery"), 
	DELIVERED(6, "Delivered", "Delivered"),
	TRANSACTION_FAILED(7, "Payment Failed","Payment Failed"),
	TRANSACTION_PENDING(8, "Payment Pending", "Payment Pending"),
	FULLY_REFUNDED(9, "Fully Refunded", "Fully Refunded"), // 
	PARTIALLY_REFUNDED(10, "Partially Refunded", "Partially Refunded"),
	ORDER_REJECTED(11, "Order Rejected", "Order Rejected"), // Initiate complete refund
	ORDER_CANCELLED(12, "Order Cancelled", "Order Cancelled"), // Once cancelled - Complete amount refunded
	PENDING_REFUND(13, "Refund Pending", "Refund Pending"),
	REFUND_REQUESTED(14, "Refund Requested", "Refund Requested"),
	REFUND_FAILED(15, "Refund Failed", "Refund Failed"),
	STORE_NOT_OPERATIONAL(16, "Payment Success", "Store not operational"),
	SECURE_RETURN_SCHEDULED(17, "SecuRe Return Scheduled", "SecuRe Return Scheduled"),
	SECURE_RETURN_INITIATED(18, "SecuRe Return Initiated", "SecuRe Return Initiated"),
    ORDER_CANCELLED_FOR_SECURE_RETURN(19, "SecuRe Return Scheduled", "SecuRe Return Scheduled"),
    RIDER_ASSIGNED_FOR_SECURE_RETURN(20, "Delivery Partner Assigned", "Delivery Partner Assigned"),
    ITEMS_PICKED_UP_FOR_SECURE_RETURN(21, "Items Picked Up For SecuRe Return", "Items Picked Up For SecuRe Return"),
    SECURE_RETURN_COMPLETED(22, "SecuRe Return Completed", "SecuRe Return Completed"),
    SECURE_RETURN_FAILED(23, "SecuRe Return Failed", "SecuRe Return Failed"),
    ITEM_SECURED(24, "Item SecuRe", "Item SecuRe"),
    DELIVERY_FAILED(25, "Delivery Failed", "Delivery Failed - Reattempt Requested"),
    ;
	// @formatter:on

    private final int id;
    private final String customer_status;
    private final String internal_status;

    static final Map<String, OrderStatus> byInternalStatus = new HashMap<>();
    static final Map<OrderStatus, String> byCustomerStatus = new HashMap<>();
    static final Map<Integer, OrderStatus> getById = new HashMap<>();

    static {
        for (OrderStatus e : OrderStatus.values()) {
            getById.put(e.getId(), e);
            byInternalStatus.put(e.getInternal_status(), e);
            byCustomerStatus.put(e, e.getCustomer_status());
        }
    }

    public static OrderStatus getById(int i) {
        return getById.getOrDefault(i, null);
    }

    public static OrderStatus getByInternalStatus(String s) {
        return byInternalStatus.getOrDefault(s, null);
    }

    public static List<OrderStatus> getByCustomerStatus(String s) {
        List<OrderStatus> list = new ArrayList<>();
        byCustomerStatus.forEach((key, val) -> {
            if (val.equals(s)) list.add(key);
        });
        return list;
    }

}
