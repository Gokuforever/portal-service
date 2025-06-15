package com.sorted.portal.service.order;

import com.phonepe.sdk.pg.common.models.PgV2InstrumentType;
import com.phonepe.sdk.pg.common.models.response.OrderStatusResponse;
import com.phonepe.sdk.pg.common.models.response.PaymentDetail;
import com.sorted.commons.beans.Item;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.MailTemplate;
import com.sorted.commons.enums.OrderStatus;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter;
import com.sorted.commons.helper.MailBuilder;
import com.sorted.commons.notifications.EmailSenderImpl;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.portal.PhonePe.PhonePeUtility;
import com.sorted.portal.response.beans.OrderItemResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusCheckService {

    private final Cart_Service cart_Service;
    private final ProductService productService;
    private final Order_Details_Service order_Details_Service;
    private final Order_Item_Service order_Item_Service;
    private final PhonePeUtility phonePeUtility;
    private final File_Upload_Details_Service file_Upload_Details_Service;
    private final EmailSenderImpl emailSenderImpl;
    private final OrderTemplateService orderTemplateService;
    private final Users_Service usersService;

    public List<OrderItemResponse> checkOrderStatus(@NonNull Order_Details order_Details) {
        OrderStatus status = order_Details.getStatus();
        boolean cartAndProductUpdated = status.equals(OrderStatus.TRANSACTION_PENDING) || status.equals(OrderStatus.TRANSACTION_PROCESSED);
        // Process payment status if needed
        boolean isPaid = processPaymentStatus(order_Details);

        List<OrderItemResponse> orderItemResponseList = getOrderItemResponses(order_Details, cartAndProductUpdated);

        log.info("status:: Successfully retrieved status for order ID: {}, with {} items",
                order_Details.getId(), orderItemResponseList.size());
        if (isPaid) {
            // TODO: send mail and sms to seller to accept or reject the order

            AggregationFilter.SEFilter filterU = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
            filterU.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            filterU.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.id, order_Details.getUser_id()));

            Users user = usersService.repoFindOne(filterU);
            if (user == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
            }
            String userName = user.getFirst_name() + " " + user.getLast_name();
            String orderTemplateTable = orderTemplateService.getOrderTemplateTable(order_Details);
            String mailContent = userName + "|" + orderTemplateTable;

            MailBuilder builder = new MailBuilder();
            builder.setTo(user.getEmail_id());
            builder.setContent(mailContent);
            builder.setTemplate(MailTemplate.DIRECT_ORDER_CONFIRMATION);
            emailSenderImpl.sendEmailHtmlTemplate(builder);
        }

        return orderItemResponseList;
    }

    private boolean processPaymentStatus(Order_Details order_Details) {
        // Only check payment status if not already completed
        if (!(StringUtils.hasText(order_Details.getPayment_status()) && order_Details.getPayment_status().equals("COMPLETED"))) {
            log.info("status:: Payment not completed, checking with PhonePe for order ID: {}", order_Details.getId());
            Optional<OrderStatusResponse> orderStatusResponseOptional = phonePeUtility.checkStatus(order_Details.getId());

            if (orderStatusResponseOptional.isEmpty()) {
                log.error("status:: Empty response from PhonePe for order ID: {}", order_Details.getId());
                throw new CustomIllegalArgumentsException(ResponseCode.PG_BAD_REQ);
            }

            OrderStatusResponse orderStatusResponse = orderStatusResponseOptional.get();
            if (CollectionUtils.isEmpty(orderStatusResponse.getPaymentDetails())) {
                log.error("status:: Invalid response from PhonePe payment status check for order ID: {}", order_Details.getId());
                throw new CustomIllegalArgumentsException(ResponseCode.PG_BAD_REQ);
            }

            List<PaymentDetail> paymentDetails = orderStatusResponse.getPaymentDetails();
            PaymentDetail paymentDetail = paymentDetails.get(paymentDetails.size() - 1);
            String state = paymentDetail.getState();
            PgV2InstrumentType paymentMode = paymentDetail.getPaymentMode();
            String transactionId = paymentDetail.getTransactionId();

            log.info("status:: Payment details - state: {}, mode: {}, transactionId: {}",
                    state, paymentMode, transactionId);

            order_Details.setPayment_status(state);
            order_Details.setPayment_mode(paymentMode != null ? paymentMode.name() : null);
            order_Details.setTransaction_id(transactionId);

            // Update order status only if payment is successful
            OrderStatus status = switch (state) {
                case "COMPLETED" -> OrderStatus.TRANSACTION_PROCESSED;
                case "FAILED" -> OrderStatus.TRANSACTION_FAILED;
                default -> OrderStatus.TRANSACTION_PENDING;
            };
            order_Details.setStatus(status, Defaults.SYSTEM_ADMIN);
            log.info("status:: Order status updated to {} for order ID: {}", status, order_Details.getId());

            order_Details_Service.update(order_Details.getId(), order_Details, Defaults.SYSTEM_ADMIN);
            return status.equals(OrderStatus.TRANSACTION_PROCESSED);
        }
        return false;
    }

    private List<OrderItemResponse> getOrderItemResponses(Order_Details order_Details, boolean cartAndProductUpdated) {
        List<OrderItemResponse> orderItemResponseList = new ArrayList<>();

        AggregationFilter.SEFilter filterOI = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filterOI.addClause(AggregationFilter.WhereClause.eq(Order_Item.Fields.order_id, order_Details.getId()));
        filterOI.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Order_Item> orderItems = order_Item_Service.repoFind(filterOI);
        if (!CollectionUtils.isEmpty(orderItems)) {
            List<String> productIds = orderItems.stream()
                    .map(Order_Item::getProduct_id)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();

            if (!CollectionUtils.isEmpty(productIds)) {
                // Handle cart updates based on transaction status
                if (!cartAndProductUpdated && (order_Details.getStatus().equals(OrderStatus.TRANSACTION_PROCESSED) || order_Details.getStatus().equals(OrderStatus.TRANSACTION_PENDING))) {
                    updateCartAfterTransaction(order_Details.getUser_id(), productIds);
                }

                Map<String, Products> mapP = getProductsMap(productIds);

                // Update product quantities if needed
                if (!cartAndProductUpdated && (order_Details.getStatus().equals(OrderStatus.TRANSACTION_PROCESSED) || order_Details.getStatus().equals(OrderStatus.TRANSACTION_PENDING))) {
                    updateProductQuantities(orderItems, mapP);
                }

                Map<String, String> mapFUD = getFileUploadDetailsMap(mapP.keySet());

                for (Order_Item item : orderItems) {
                    OrderItemResponse response = convertToResponse(mapP, item, mapFUD);
                    if (response != null) {
                        orderItemResponseList.add(response);
                    }
                }
            }
        }
        return orderItemResponseList;
    }

    /**
     * Updates the user's cart by removing products that are part of this transaction
     *
     * @param userId     User ID of the cart owner
     * @param productIds List of product IDs to remove from cart
     */
    private void updateCartAfterTransaction(String userId, List<String> productIds) {
        AggregationFilter.SEFilter filterC = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filterC.addClause(AggregationFilter.WhereClause.eq(Cart.Fields.user_id, userId));
        filterC.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        Cart cart = cart_Service.repoFindOne(filterC);

        if (cart == null) {
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }

        List<Item> filteredItems = cart.getCart_items().stream()
                .filter(e -> !productIds.contains(e.getProduct_id()))
                .toList();

        if (!filteredItems.isEmpty()) {
            cart.setCart_items(filteredItems);
        } else {
            cart.setCart_items(null);
        }

        cart_Service.update(cart.getId(), cart, Defaults.SYSTEM_ADMIN);
    }

    /**
     * Retrieves a map of products by their IDs
     *
     * @param productIds List of product IDs to look up
     * @return Map of product IDs to Products
     */
    private Map<String, Products> getProductsMap(List<String> productIds) {
        AggregationFilter.SEFilter filterP = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filterP.addClause(AggregationFilter.WhereClause.in(BaseMongoEntity.Fields.id, productIds));
        filterP.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Products> products = productService.repoFind(filterP);
        return !CollectionUtils.isEmpty(products) ?
                products.stream().collect(Collectors.toMap(Products::getId, product -> product, (p1, p2) -> p1)) :
                new HashMap<>();
    }

    /**
     * Updates product quantities after a successful transaction
     *
     * @param orderItems  List of order items that were purchased
     * @param productsMap Map of product IDs to Products
     */
    private void updateProductQuantities(List<Order_Item> orderItems, Map<String, Products> productsMap) {
        for (Order_Item orderItem : orderItems) {
            Products product = productsMap.getOrDefault(orderItem.getProduct_id(), null);
            if (product == null) {
                continue;
            }

            Long currentQuantity = product.getQuantity();
            Long orderedQuantity = orderItem.getQuantity();
            long updatedQuantity = currentQuantity - orderedQuantity;

            if (updatedQuantity < 0) {
                updatedQuantity = 0L;
            }

            product.setQuantity(updatedQuantity);
            productService.update(product.getId(), product, Defaults.SYSTEM_ADMIN);
        }
    }

    /**
     * Retrieves file upload details for products
     *
     * @param productIds Set of product IDs to find file upload details for
     * @return Map of product IDs to file document IDs
     */
    private Map<String, String> getFileUploadDetailsMap(Set<String> productIds) {
        Map<String, String> mapFUD = new HashMap<>();

        if (!productIds.isEmpty()) {
            AggregationFilter.SEFilter filterFUD = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
            filterFUD.addClause(AggregationFilter.WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(productIds)));
            filterFUD.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            List<File_Upload_Details> listFUD = file_Upload_Details_Service.repoFind(filterFUD);
            if (!CollectionUtils.isEmpty(listFUD)) {
                listFUD.forEach(fud -> {
                    if (StringUtils.hasText(fud.getId()) && StringUtils.hasText(fud.getDocument_id())) {
                        mapFUD.putIfAbsent(fud.getId(), fud.getDocument_id());
                    }
                });
            }
        }

        return mapFUD;
    }

    private OrderItemResponse convertToResponse(Map<String, Products> mapP, Order_Item orderItem, Map<String, String> mapFUD) {
        if (orderItem == null || !StringUtils.hasText(orderItem.getProduct_id())) {
            log.warn("convertToResponse:: Invalid order item or missing product ID");
            return null;
        }

        Products product = mapP.getOrDefault(orderItem.getProduct_id(), null);
        String productName = (product != null) ? product.getName() : "";
        String documentId = mapFUD.getOrDefault(orderItem.getProduct_id(), null);
        String purchaseType = (orderItem.getType() != null) ? orderItem.getType().name() : "";

        return OrderItemResponse.builder()
                .productCode(orderItem.getProduct_code())
                .productId(orderItem.getProduct_id())
                .productName(productName)
                .documentId(documentId)
                .purchaseType(purchaseType)
                .quantity(orderItem.getQuantity())
                .sellingPrice(orderItem.getSelling_price())
                .totalCost(orderItem.getTotal_cost())
                .build();
    }
}
