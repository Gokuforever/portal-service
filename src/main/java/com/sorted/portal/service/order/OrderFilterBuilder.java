package com.sorted.portal.service.order;

import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Order_Details;
import com.sorted.commons.entity.mongo.Order_Item;
import com.sorted.commons.enums.OrderStatus;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.*;
import com.sorted.commons.helper.Pagination;
import com.sorted.portal.request.beans.FindOrderReqBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for building filters for order queries
 */
@Service
@Slf4j
public class OrderFilterBuilder {

    private static final List<OrderStatus> SELLER_ALLOWED_STATUS = List.of(
            OrderStatus.TRANSACTION_PROCESSED,
            OrderStatus.STORE_NOT_OPERATIONAL,
            OrderStatus.ORDER_ACCEPTED,
            OrderStatus.READY_FOR_PICK_UP,
            OrderStatus.RIDER_ASSIGNED,
            OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.DELIVERED);

    private final int defaultPage;
    private final int defaultSize;

    /**
     * Constructor with pagination defaults
     *
     * @param defaultPage Default page number
     * @param defaultSize Default page size
     */
    public OrderFilterBuilder(
            @Value("${se.default.page}") int defaultPage,
            @Value("${se.default.size}") int defaultSize) {
        this.defaultPage = defaultPage;
        this.defaultSize = defaultSize;
    }

    /**
     * Build filter for orders based on user role and search criteria
     *
     * @param req       The request bean containing search criteria
     * @param usersBean The current user details
     * @return SEFilter object for order queries
     */
    public SEFilter buildOrderFilter(FindOrderReqBean req, UsersBean usersBean) {
        log.debug("Building order filter with search criteria for user: {}", req.getReq_user_id());

        SEFilter filterOD = new SEFilter(SEFilterType.AND);

        // Apply user-specific filters based on role
        applyUserRoleFilters(filterOD, usersBean, req);

        // Apply optional filters based on request parameters
        applyCodeFilter(filterOD, req);
        applyDateRangeFilter(filterOD, req);

        // Always filter out deleted records
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        // Configure sorting and pagination
        configureOrderingAndPagination(filterOD, req);

        return filterOD;
    }

    public SEFilter buildOrderFindOneFilter(FindOrderReqBean req, UsersBean usersBean) {
        log.debug("Building order fineOne filter with search criteria for user: {}", req.getReq_user_id());

        SEFilter filterOD = new SEFilter(SEFilterType.AND);

        // Apply user-specific filters based on role
        applyUserRoleFilters(filterOD, usersBean, req);

        // Apply optional filters based on request parameters
        applyCodeFilter(filterOD, req);

        // Always filter out deleted records
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        return filterOD;
    }


    /**
     * Build filter for order items by order IDs
     *
     * @param orderIds List of order IDs to fetch items for
     * @return SEFilter for order items
     */
    public SEFilter buildOrderItemsFilter(List<String> orderIds) {
        log.debug("Building order items filter for {} orders", orderIds.size());

        SEFilter filterOI = new SEFilter(SEFilterType.AND);
        filterOI.addClause(WhereClause.in(Order_Item.Fields.order_id, orderIds));
        filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        return filterOI;
    }

    public SEFilter buildOrderItemsFilter(String orderId) {
        log.debug("Building find order items for order id {}", orderId);

        SEFilter filterOI = new SEFilter(SEFilterType.AND);
        filterOI.addClause(WhereClause.eq(Order_Item.Fields.order_id, orderId));
        filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        return filterOI;
    }



        /**
         * Build filter for products by IDs
         *
         * @param productIds List of product IDs
         * @return SEFilter for products
         */
    public SEFilter buildProductFilter(List<String> productIds) {
        log.debug("Building products filter for {} products", productIds.size());

        SEFilter filterP = new SEFilter(SEFilterType.AND);
        filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, productIds));

        return filterP;
    }

    /**
     * Apply filters based on user role
     */
    private void applyUserRoleFilters(SEFilter filterOD, UsersBean usersBean, FindOrderReqBean req) {
        switch (usersBean.getRole().getUser_type()) {
            case SELLER:
                log.debug("Applying seller-specific filter for seller ID: {}", usersBean.getSeller().getId());
                filterOD.addClause(WhereClause.eq(Order_Details.Fields.seller_id, usersBean.getSeller().getId()));
                applySellerStatusFilter(filterOD, req);
                break;

            case CUSTOMER, GUEST:
                log.debug("Applying customer-specific filter for customer ID: {}", usersBean.getId());
                filterOD.addClause(WhereClause.eq(Order_Details.Fields.user_id, usersBean.getId()));
                applyCustomerStatusFilter(filterOD, req);
                break;

            case SUPER_ADMIN:
                log.debug("User is SUPER_ADMIN, no specific role filter applied");
                applyStatusFilter(filterOD, req);
                break;

            default:
                log.warn("Role filtering not applied for user type: {}", usersBean.getRole().getUser_type());
        }
    }

    /**
     * Apply seller-specific status filter
     */
    private void applySellerStatusFilter(SEFilter filterOD, FindOrderReqBean req) {
        if (StringUtils.hasText(req.getOrder_status())) {
            OrderStatus orderStatus = OrderStatus.getByInternalStatus(req.getOrder_status());
            if (orderStatus == null || !SELLER_ALLOWED_STATUS.contains(orderStatus)) {
                log.warn("Invalid status for seller: {}", req.getOrder_status());
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_ORDER_STATUS);
            }
            log.debug("Applying status filter for seller: {}", orderStatus);
            filterOD.addClause(WhereClause.eq(Order_Details.Fields.status_id, orderStatus.getId()));
        } else {
            List<Integer> statusIds = SELLER_ALLOWED_STATUS.stream()
                    .map(OrderStatus::getId)
                    .collect(Collectors.toList());
            filterOD.addClause(WhereClause.in(Order_Details.Fields.status_id, statusIds));
        }
    }

    /**
     * Apply customer-specific status filter
     */
    private void applyCustomerStatusFilter(SEFilter filterOD, FindOrderReqBean req) {
        if (StringUtils.hasText(req.getOrder_status())) {
            List<OrderStatus> orderStatuses = getByCustomerStatus(req.getOrder_status());
            if (orderStatuses.isEmpty()) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_ORDER_STATUS);
            }
            log.debug("Applying customer status filter: {}", orderStatuses);
            List<Integer> statusIds = orderStatuses.stream()
                    .map(OrderStatus::getId)
                    .collect(Collectors.toList());
            filterOD.addClause(WhereClause.in(Order_Details.Fields.status_id, statusIds));
        }
    }

    /**
     * Apply general status filter (for admin)
     */
    private void applyStatusFilter(SEFilter filterOD, FindOrderReqBean req) {
        if (StringUtils.hasText(req.getOrder_status())) {
            OrderStatus orderStatus = OrderStatus.getByInternalStatus(req.getOrder_status());
            if (orderStatus == null) {
                log.warn("Invalid status: {}", req.getOrder_status());
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_ORDER_STATUS);
            }
            log.debug("Applying status filter: {}", orderStatus);
            filterOD.addClause(WhereClause.eq(Order_Details.Fields.status_id, orderStatus.getId()));
        }
    }

    /**
     * Apply code filter if provided
     */
    private void applyCodeFilter(SEFilter filterOD, FindOrderReqBean req) {
        if (StringUtils.hasText(req.getCode())) {
            log.debug("Applying code filter: {}", req.getCode());
            filterOD.addClause(WhereClause.eq(Order_Details.Fields.code, req.getCode()));
        }
    }

    /**
     * Apply date range filter if provided
     */
    private void applyDateRangeFilter(SEFilter filterOD, FindOrderReqBean req) {
        if (StringUtils.hasText(req.getFrom_date()) && StringUtils.hasText(req.getTo_date())) {
            LocalDateTime from = LocalDate.parse(req.getFrom_date()).atTime(LocalTime.MIN);
            LocalDateTime to = LocalDate.parse(req.getTo_date()).atTime(LocalTime.MAX);
            log.debug("Applying date range filter from {} to {}", from, to);
            filterOD.addClause(WhereClause.gte(BaseMongoEntity.Fields.creation_date, from));
            filterOD.addClause(WhereClause.lte(BaseMongoEntity.Fields.creation_date, to));
        }
    }

    /**
     * Configure ordering and pagination
     */
    private void configureOrderingAndPagination(SEFilter filterOD, FindOrderReqBean req) {
        OrderBy orderBy = new OrderBy(BaseMongoEntity.Fields.creation_date, SortOrder.DESC);
        filterOD.setOrderBy(orderBy);

        int page = req.getPage() < 0 ? defaultPage : req.getPage();
        int size = req.getSize() < 1 ? defaultSize : req.getSize();
        log.debug("Setting pagination - page: {}, size: {}", page, size);

        Pagination pagination = new Pagination(page, size);
        filterOD.setPagination(pagination);
    }

    /**
     * Get OrderStatus list from customer status string
     */
    private List<OrderStatus> getByCustomerStatus(String customerStatus) {
        log.debug("Getting order statuses for customer status: {}", customerStatus);

        return switch (customerStatus.toUpperCase()) {
            case "PENDING" -> List.of(
                    OrderStatus.TRANSACTION_PROCESSED,
                    OrderStatus.ORDER_ACCEPTED
            );
            case "PROCESSING" -> List.of(
                    OrderStatus.READY_FOR_PICK_UP,
                    OrderStatus.RIDER_ASSIGNED,
                    OrderStatus.OUT_FOR_DELIVERY
            );
            case "DELIVERED" -> List.of(OrderStatus.DELIVERED);
            case "CANCELLED" -> List.of(
                    OrderStatus.ORDER_REJECTED,
                    OrderStatus.PENDING_REFUND,
                    OrderStatus.REFUND_REQUESTED,
                    OrderStatus.REFUND_FAILED,
                    OrderStatus.FULLY_REFUNDED
            );
            default -> {
                log.warn("Unknown customer status: {}", customerStatus);
                yield List.of();
            }
        };
    }
} 