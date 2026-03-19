package com.sorted.portal.service.order;

import com.phonepe.sdk.pg.common.models.response.RefundResponse;
import com.sorted.common.beans.AddressDTO;
import com.sorted.common.beans.Spoc_Details;
import com.sorted.common.beans.UsersBean;
import com.sorted.common.entity.mongo.*;
import com.sorted.common.entity.service.Order_Details_Service;
import com.sorted.common.entity.service.Order_Item_Service;
import com.sorted.common.entity.service.ProductService;
import com.sorted.common.enums.MailTemplate;
import com.sorted.common.enums.OrderStatus;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.helper.SEResponse;
import com.sorted.common.porter.res.beans.CreateOrderResBean;
import com.sorted.common.porter.res.beans.FetchOrderRes.FareDetails;
import com.sorted.common.utils.CommonUtils;
import com.sorted.common.utils.InternalMailService;
import com.sorted.common.utils.PorterUtility;
import com.sorted.portal.PhonePe.PhonePeUtility;
import com.sorted.portal.request.beans.CreateDeliveryBean;
import com.sorted.portal.request.beans.OrderAcceptRejectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service for processing order operations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderProcessingService {

    private final Order_Details_Service orderDetailsService;
    private final Order_Item_Service orderItemService;
    private final OrderValidationService validationService;
    private final OrderDeliveryService deliveryService;
    private final PhonePeUtility phonePeUtility;
    private final InternalMailService internalMailService;
    private final ProductService productService;
    private final PorterUtility porterUtility;

    /**
     * Process ready for pickup operation
     *
     * @param req Request bean
     * @return SEResponse with updated order details
     */
    public SEResponse processReadyForPickup(CreateDeliveryBean req) {
        log.info("Processing ready for pickup for order ID: {}", req.getOrder_id());

        try {
            // Validate user
            UsersBean usersBean = validationService.validateForReadyForPickup(req);

            // Validate order
            Order_Details orderDetails = validationService.validateOrderForReadyForPickup(
                    req.getOrder_id(), usersBean.getSeller().getId());

            // Get order items
            List<Order_Item> orderItems = validationService.getOrderItems(req.getOrder_id());

            // Validate SPOC
            Spoc_Details spocDetails = validationService.validatePrimarySpoc(usersBean.getSeller());

            // Get addresses
            AddressDTO deliveryAddress = orderDetails.getDelivery_address();
            AddressDTO pickupAddress = orderDetails.getPickup_address();

            // Get customer
            Users customer = validationService.getOrderUser(orderDetails.getUser_id());

            // Create delivery order
            CreateOrderResBean deliveryResponse = deliveryService.createDeliveryOrder(
                    orderDetails, spocDetails, deliveryAddress, pickupAddress, customer);

            // Update order status
            updateOrderForReadyForPickup(orderDetails, orderItems, deliveryResponse, usersBean.getId());

            log.info("Successfully processed ready for pickup for order ID: {}", req.getOrder_id());
            return SEResponse.getBasicSuccessResponseObject(orderDetails, ResponseCode.READY_FOR_DISPATCH);

        } catch (CustomIllegalArgumentsException ex) {
            log.error("Validation error for ready for pickup: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            log.error("Unexpected error processing ready for pickup", e);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    /**
     * Process order accept or reject operation
     *
     * @param req Request bean
     * @return SEResponse indicating success or failure
     */
    public SEResponse processAcceptReject(OrderAcceptRejectRequest req) {
        log.info("Processing order accept/reject for order ID: {}", req.getOrderId());

        // Validate user
        UsersBean usersBean = validationService.validateForAcceptReject(req);

        // Validate order
        Order_Details orderDetails = validationService.validateOrderForAcceptReject(
                usersBean.getSeller().getId(), req.getOrderId());

        // Check for partial accept scenario
        if (req.isPartialAccept()) {
            return processPartialAccept(orderDetails, req, usersBean.getId());
        } else if (req.isAccepted()) {
            return processOrderAccept(orderDetails, usersBean.getId());
        } else {
            return processOrderReject(orderDetails, req.getRemark(), usersBean.getId());
        }
    }

    /**
     * Process order acceptance
     *
     * @param orderDetails Order details
     * @param userId       User ID
     * @return SEResponse indicating success
     */
    private SEResponse processOrderAccept(Order_Details orderDetails, String userId) {
        log.info("Processing order acceptance for order ID: {}", orderDetails.getId());

        orderDetails.setStatus(OrderStatus.ORDER_ACCEPTED, userId);
        orderDetailsService.update(orderDetails.getId(), orderDetails, userId);

        log.info("Order accepted successfully: {}", orderDetails.getId());
        return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
    }

    /**
     * Process order rejection
     *
     * @param orderDetails Order details
     * @param remarks      Rejection remarks
     * @param userId       User ID
     * @return SEResponse indicating success
     */
    private SEResponse processOrderReject(Order_Details orderDetails, String remarks, String userId) {
        log.info("Processing order rejection for order ID: {}", orderDetails.getId());

        orderDetails.setStatus(OrderStatus.ORDER_REJECTED, userId);
        orderDetails.setRejection_remarks(remarks);
        orderDetailsService.update(orderDetails.getId(), orderDetails, userId);

        // TODO: mark products out of stock

        SEFilter filterOI = new SEFilter(SEFilterType.AND);
        filterOI.addClause(WhereClause.eq(Order_Item.Fields.order_id, orderDetails.getId()));
        filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Order_Item> orderItems = orderItemService.repoFind(filterOI);
        if (!CollectionUtils.isEmpty(orderItems)) {
            List<String> productIds = orderItems.stream().map(Order_Item::getProduct_id).distinct().toList();
            SEFilter filterP = new SEFilter(SEFilterType.AND);
            filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, productIds));
            filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            List<Products> products = productService.repoFind(filterP);
            if (!CollectionUtils.isEmpty(products)) {
                products.forEach(product -> product.setQuantity(0L));
                for (Products product : products) {
                    productService.update(product.getId(), product, "On Reject");
                }
            }
        }

        long nanoseconds = CommonUtils.getNanoseconds();
        String refundTxnId = "REF" +
                LocalDate.now().getMonth() +
                Year.now() +
                nanoseconds;

        orderDetails.setRefund_transaction_id(refundTxnId);
        orderDetails.setStatus(OrderStatus.REFUND_REQUESTED, userId);
        orderDetailsService.update(orderDetails.getId(), orderDetails, userId);
        // Process refund
        Optional<RefundResponse> refundResponse = phonePeUtility.refund(refundTxnId, orderDetails.getId(), orderDetails.getTotal_amount());
        if (refundResponse.isEmpty()) {
            return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
        }

        RefundResponse response = refundResponse.get();
        String state = response.getState();
        OrderStatus orderStatus = switch (state) {
            case "COMPLETED" -> OrderStatus.FULLY_REFUNDED;
            case "FAILED" -> OrderStatus.REFUND_FAILED;
            default -> OrderStatus.PENDING_REFUND;
        };

        orderDetails.setStatus(orderStatus, userId);
        orderDetailsService.update(orderDetails.getId(), orderDetails, userId);

        if (orderStatus == OrderStatus.REFUND_FAILED) {
            internalMailService.sendMailOnError("Refund failed for order ID: " + orderDetails.getId(), "Refund failed for order ID: " + orderDetails.getId(), null);
        }

        porterUtility.sendMailWithOrderDetails(orderDetails, MailTemplate.ORDER_REJECTED, null);

        return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
    }

    /**
     * Handle failed refund initiation
     */
    private SEResponse handleFailedRefundInitiation(Order_Details orderDetails, String userId) {
        log.warn("Failed to initiate refund for order: {}", orderDetails.getId());

        orderDetails.setStatus(OrderStatus.PENDING_REFUND, userId);
        // TODO: initiate internal email
        // TODO: notify customer about rejection and pending refund request
        orderDetailsService.update(orderDetails.getId(), orderDetails, userId);

        return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
    }

    /**
     * Update order and order items for ready for pickup
     */
    private void updateOrderForReadyForPickup(
            Order_Details orderDetails,
            List<Order_Item> orderItems,
            CreateOrderResBean deliveryResponse,
            String userId) {

        log.debug("Updating order status to READY_FOR_PICK_UP for order ID: {}", orderDetails.getId());

        // Update order details
        orderDetails.setStatus(OrderStatus.READY_FOR_PICK_UP, userId);
        orderDetails.setDp_order_id(deliveryResponse.getOrder_id());
        orderDetails.setFare_details(FareDetails.builder()
                .estimated_fare_details(deliveryResponse.getEstimated_fare_details())
                .build());
        orderDetails.setEstimated_pickup_time(deliveryResponse.getEstimated_pickup_time());

        // Save order details
        orderDetailsService.update(orderDetails.getId(), orderDetails, userId);
    }

    /**
     * Process partial order acceptance - accept some items, reject others and initiate refund
     *
     * @param orderDetails Order details
     * @param req          Request with accepted item IDs (non-accepted items are rejected)
     * @param userId       User ID
     * @return SEResponse indicating success
     */
    private SEResponse processPartialAccept(Order_Details orderDetails, OrderAcceptRejectRequest req, String userId) {
        log.info("Processing partial order acceptance for order ID: {}", orderDetails.getId());

        // Get all order items
        SEFilter filterOI = new SEFilter(SEFilterType.AND);
        filterOI.addClause(WhereClause.eq(Order_Item.Fields.order_id, orderDetails.getId()));
        filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Order_Item> orderItems = orderItemService.repoFind(filterOI);

        if (CollectionUtils.isEmpty(orderItems)) {
            throw new CustomIllegalArgumentsException("No order items found");
        }

        // Validate that all accepted item IDs belong to this order
        List<String> allOrderItemIds = orderItems.stream().map(Order_Item::getId).toList();
        validateItemIds(req.getAcceptedItemIds(), allOrderItemIds);

        // Process items - accepted items get ORDER_ACCEPTED, others get ITEM_REJECTED
        long refundAmount = 0L;
        List<Order_Item> rejectedItems = new ArrayList<>();
        List<Order_Item> acceptedItems = new ArrayList<>();

        for (Order_Item item : orderItems) {
            if (req.getAcceptedItemIds().contains(item.getId())) {
                item.setStatus(OrderStatus.ORDER_ACCEPTED, userId);
                orderItemService.update(item.getId(), item, userId);
                acceptedItems.add(item);
            } else {
                // Items not in accepted list are automatically rejected
                item.setStatus(OrderStatus.ITEM_REJECTED, userId);
                orderItemService.update(item.getId(), item, userId);
                refundAmount += item.getTotal_cost() != null ? item.getTotal_cost() : 0L;
                rejectedItems.add(item);
            }
        }

        // Update order status to PARTIALLY_ACCEPTED
        orderDetails.setStatus(OrderStatus.PARTIALLY_ACCEPTED, userId);
        orderDetails.setRejection_remarks(req.getRejectionReason());
        orderDetails.setPartial_refund_amount(refundAmount);
        orderDetailsService.update(orderDetails.getId(), orderDetails, userId);

        // Mark rejected products as out of stock
        markRejectedProductsOutOfStock(rejectedItems, userId);

        // Initiate refund for rejected items
        if (refundAmount > 0) {
            initiatePartialRefund(orderDetails, refundAmount, userId);
        }

        log.info("Partial order acceptance completed. Accepted: {}, Rejected: {}, Refund amount: {}",
                acceptedItems.size(), rejectedItems.size(), refundAmount);

        return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
    }

    /**
     * Validate that all item IDs in the list belong to the order
     */
    private void validateItemIds(List<String> itemIds, List<String> allOrderItemIds) {
        if (itemIds == null) return;
        for (String itemId : itemIds) {
            if (!allOrderItemIds.contains(itemId)) {
                throw new CustomIllegalArgumentsException("Invalid item ID: " + itemId);
            }
        }
    }

    /**
     * Mark products of rejected items as out of stock
     */
    private void markRejectedProductsOutOfStock(List<Order_Item> rejectedItems, String userId) {
        if (CollectionUtils.isEmpty(rejectedItems)) return;

        List<String> productIds = rejectedItems.stream()
                .map(Order_Item::getProduct_id)
                .distinct()
                .toList();

        SEFilter filterP = new SEFilter(SEFilterType.AND);
        filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, productIds));
        filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Products> products = productService.repoFind(filterP);

        if (!CollectionUtils.isEmpty(products)) {
            products.forEach(product -> product.setQuantity(0L));
            for (Products product : products) {
                productService.update(product.getId(), product, "On Partial Reject");
            }
        }
    }

    /**
     * Initiate refund for partial rejection
     */
    private void initiatePartialRefund(Order_Details orderDetails, long refundAmount, String userId) {
        log.info("Initiating partial refund of {} for order ID: {}", refundAmount, orderDetails.getId());

        long nanoseconds = CommonUtils.getNanoseconds();
        String refundTxnId = "PREF" +
                LocalDate.now().getMonth() +
                Year.now() +
                nanoseconds;

        orderDetails.setPartial_refund_transaction_id(refundTxnId);
        orderDetailsService.update(orderDetails.getId(), orderDetails, userId);

        // Process refund via PhonePe
        Optional<RefundResponse> refundResponse = phonePeUtility.refund(
                refundTxnId, orderDetails.getId(), refundAmount);

        if (refundResponse.isEmpty()) {
            log.warn("Partial refund initiation returned empty response for order: {}", orderDetails.getId());
            updateRejectedItemsRefundStatus(orderDetails.getId(), OrderStatus.ITEM_REFUND_INITIATED, userId);
            return;
        }

        RefundResponse response = refundResponse.get();
        String state = response.getState();

        OrderStatus itemRefundStatus = switch (state) {
            case "COMPLETED" -> OrderStatus.FULLY_REFUNDED;
            case "FAILED" -> OrderStatus.REFUND_FAILED;
            default -> OrderStatus.ITEM_REFUND_INITIATED;
        };

        // Update rejected items with refund status
        updateRejectedItemsRefundStatus(orderDetails.getId(), itemRefundStatus, userId);

        if (itemRefundStatus == OrderStatus.REFUND_FAILED) {
            internalMailService.sendMailOnError(
                    "Partial refund failed for order ID: " + orderDetails.getId(),
                    "Partial refund of " + refundAmount + " failed for order ID: " + orderDetails.getId(),
                    null);
        }

        log.info("Partial refund status: {} for order ID: {}", state, orderDetails.getId());
    }

    /**
     * Update refund status for rejected items
     */
    private void updateRejectedItemsRefundStatus(String orderId, OrderStatus status, String userId) {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Order_Item.Fields.order_id, orderId));
        filter.addClause(WhereClause.eq(Order_Item.Fields.status, OrderStatus.ITEM_REJECTED));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Order_Item> rejectedItems = orderItemService.repoFind(filter);
        for (Order_Item item : rejectedItems) {
            item.setStatus(status, userId);
            orderItemService.update(item.getId(), item, userId);
        }
    }
}