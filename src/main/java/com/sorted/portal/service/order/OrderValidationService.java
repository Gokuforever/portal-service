package com.sorted.portal.service.order;

import com.sorted.commons.beans.Spoc_Details;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.Order_Details_Service;
import com.sorted.commons.entity.service.Order_Item_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.*;
import com.sorted.commons.exceptions.AccessDeniedException;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.portal.request.beans.CreateDeliveryBean;
import com.sorted.portal.request.beans.OrderAcceptRejectRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Service class for order validation operations
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderValidationService {

    private final Order_Details_Service orderDetailsService;
    private final Order_Item_Service orderItemService;
    private final Users_Service usersService;

    /**
     * Validate user for ready-for-pickup operation
     *
     * @param req Request bean
     * @return Validated user bean
     */
    public UsersBean validateForReadyForPickup(CreateDeliveryBean req) {
        log.debug("Validating user permissions for ready-for-pickup: user ID {}", req.getReq_user_id());

        // Validate user permissions
        UsersBean usersBean = usersService.validateUserForActivity(
                req.getReq_user_id(),
                Permission.EDIT,
                Activity.INVENTORY_MANAGEMENT);

        // Check user type
        Role role = usersBean.getRole();
        UserType userType = role.getUser_type();
        if (userType != UserType.SELLER) {
            log.warn("Access denied for user type: {}", userType);
            throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }

        // Validate order ID
        if (!StringUtils.hasText(req.getOrder_id())) {
            log.warn("Order ID is mandatory");
            throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_ORDER_ID);
        }

        return usersBean;
    }

    /**
     * Validate order details for ready-for-pickup operation
     *
     * @param orderId  Order ID
     * @param sellerId Seller ID
     * @return Validated order details
     */
    public Order_Details validateOrderForReadyForPickup(String orderId, String sellerId) {
        log.debug("Validating order details for order ID: {}", orderId);

        // Retrieve order details
        SEFilter filterOD = new SEFilter(SEFilterType.AND);
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, orderId));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Order_Details orderDetails = orderDetailsService.repoFindOne(filterOD);
        if (orderDetails == null) {
            log.warn("No order found with ID: {}", orderId);
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }

        // Validate order status
        if (orderDetails.getStatus() != OrderStatus.ORDER_ACCEPTED) {
            log.warn("Invalid order status: {} for order ID: {}", orderDetails.getStatus(), orderId);
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_ORDER_STATUS);
        }

        // Get order items
        List<Order_Item> orderItems = getOrderItems(orderId);

        // Validate seller
        validateSellerForOrder(orderItems, sellerId);

        return orderDetails;
    }

    /**
     * Validate primary SPOC for seller
     *
     * @param seller Seller entity
     * @return Primary SPOC details
     */
    public Spoc_Details validatePrimarySpoc(Seller seller) {
        log.debug("Validating primary SPOC for seller ID: {}", seller.getId());

        Optional<Spoc_Details> primarySpoc = seller.getSpoc_details().stream()
                .filter(Spoc_Details::isPrimary)
                .findFirst();

        if (primarySpoc.isEmpty()) {
            log.warn("Missing primary SPOC for seller ID: {}", seller.getId());
            throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRIMARY_SPOC);
        }

        return primarySpoc.get();
    }

    /**
     * Get order items for an order
     *
     * @param orderId Order ID
     * @return List of order items
     */
    public List<Order_Item> getOrderItems(String orderId) {
        log.debug("Fetching order items for order ID: {}", orderId);

        SEFilter filterOI = new SEFilter(SEFilterType.AND);
        filterOI.addClause(WhereClause.eq(Order_Item.Fields.order_id, orderId));
        filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Order_Item> listOI = orderItemService.repoFind(filterOI);
        if (CollectionUtils.isEmpty(listOI)) {
            log.warn("No order items found for order ID: {}", orderId);
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }

        return listOI;
    }

    /**
     * Get user for an order
     *
     * @param userId User ID
     * @return User entity
     */
    public Users getOrderUser(String userId) {
        log.debug("Fetching user details for user ID: {}", userId);

        SEFilter filterU = new SEFilter(SEFilterType.AND);
        filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, userId));
        filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        Users users = usersService.repoFindOne(filterU);
        if (users == null) {
            log.warn("User not found with ID: {}", userId);
            throw new CustomIllegalArgumentsException(ResponseCode.INTERNAL_SERVER_ERROR);
        }

        return users;
    }

    /**
     * Validate user for order accept/reject operation
     *
     * @param req Accept/reject request
     * @return Validated user bean
     */
    public UsersBean validateForAcceptReject(OrderAcceptRejectRequest req) {
        log.debug("Validating user for accept/reject: {}", req.getReq_user_id());

        UsersBean usersBean = usersService.validateUserForActivity(
                req.getReq_user_id(),
                Permission.EDIT,
                Activity.INVENTORY_MANAGEMENT);

        if (!usersBean.getRole().getUser_type().equals(UserType.SELLER)) {
            log.warn("Access denied: User is not a seller");
            throw new AccessDeniedException();
        }

        if (!StringUtils.hasText(req.getOrderId())) {
            log.warn("Missing order ID in request");
            throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ORDER_ID);
        }

        return usersBean;
    }

    /**
     * Validate order for accept/reject operation
     *
     * @param sellerId Seller ID
     * @param orderId  Order ID
     * @return Validated order details
     */
    public Order_Details validateOrderForAcceptReject(String sellerId, String orderId) {
        log.debug("Validating order for accept/reject: orderID={}, sellerID={}", orderId, sellerId);

        SEFilter filterOD = new SEFilter(SEFilterType.AND);
        filterOD.addClause(WhereClause.eq(Order_Details.Fields.seller_id, sellerId));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, orderId));

        Order_Details orderDetails = orderDetailsService.repoFindOne(filterOD);
        if (orderDetails == null) {
            log.warn("No order found with ID: {} for seller: {}", orderId, sellerId);
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }

        switch (orderDetails.getStatus()) {
            case TRANSACTION_PROCESSED:
            case STORE_NOT_OPERATIONAL:
                break;
            default:
                log.warn("Invalid order status: {} for accept/reject operation", orderDetails.getStatus());
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_ORDER_STATUS);
        }
        return orderDetails;
    }

    /**
     * Validate seller for order items
     *
     * @param orderItems List of order items
     * @param sellerId   Seller ID to validate
     */
    private void validateSellerForOrder(List<Order_Item> orderItems, String sellerId) {
        List<String> sellerIds = orderItems.stream()
                .map(Order_Item::getSeller_id)
                .distinct()
                .toList();

        if (sellerIds.size() > 1) {
            log.warn("Multiple sellers found for order");
            // TODO: need to discuss multi-seller handling
        }

        String orderSellerId = sellerIds.get(0);
        if (!sellerId.equals(orderSellerId)) {
            log.warn("Invalid seller for order. Expected: {}, Actual: {}", orderSellerId, sellerId);
            throw new CustomIllegalArgumentsException(ResponseCode.INVALID_SELLER_FOR_ORDER);
        }
    }
} 