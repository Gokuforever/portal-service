package com.sorted.portal.service.order;

import com.sorted.commons.entity.mongo.Order_Details;
import com.sorted.commons.entity.mongo.Order_Item;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.portal.enums.OrderItemsProperties;
import com.sorted.portal.enums.OrderProperties;
import com.sorted.portal.response.beans.FindOrderResBean;
import com.sorted.portal.response.beans.OrderItemDTO;
import com.sorted.portal.response.beans.OrderItemReportsDTO;
import com.sorted.portal.response.beans.OrderReportDTO;
import com.sorted.portal.service.FileGeneratorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service for mapping order entities to response objects
 */
@Service
@Slf4j
public class OrderResponseMapper {

    /**
     * Convert order details entity to response bean for internal users
     *
     * @param orderDetails  Order details entity
     * @param orderItemsMap Map of order items by order ID
     * @param productsMap   Map of products by product ID
     * @return FindOrderResBean
     */
    public FindOrderResBean mapToInternalResponse(
            Order_Details orderDetails,
            Map<String, List<Order_Item>> orderItemsMap,
            Map<String, Products> productsMap) {

        log.debug("Mapping order details to internal response for order ID: {}", orderDetails.getId());

        return FindOrderResBean.builder()
                .id(orderDetails.getId())
                .code(orderDetails.getCode())
                .status(orderDetails.getStatus().getInternal_status())
                .transaction_id(orderDetails.getTransaction_id())
                .total_amount(orderDetails.getTotal_amount())
                .pickup_address(orderDetails.getPickup_address())
                .delivery_address(orderDetails.getDelivery_address())
                .orderItems(mapOrderItems(orderDetails.getId(), orderItemsMap, productsMap))
                .creation_date_str(orderDetails.getCreation_date_str())
                .build();
    }

    /**
     * Convert order details entity to response bean for customers
     *
     * @param orderDetails  Order details entity
     * @param orderItemsMap Map of order items by order ID
     * @param productsMap   Map of products by product ID
     * @return FindOrderResBean
     */
    public FindOrderResBean mapToCustomerResponse(
            Order_Details orderDetails,
            Map<String, List<Order_Item>> orderItemsMap,
            Map<String, Products> productsMap) {

        log.debug("Mapping order details to customer response for order ID: {}", orderDetails.getId());

        return FindOrderResBean.builder()
                .id(orderDetails.getId())
                .code(orderDetails.getCode())
                .status(orderDetails.getStatus().getCustomer_status())
                .total_amount(orderDetails.getTotal_amount())
                .orderItems(mapOrderItems(orderDetails.getId(), orderItemsMap, productsMap))
                .creation_date_str(orderDetails.getCreation_date_str())
                .build();
    }

    /**
     * Map a list of order items to DTOs
     *
     * @param orderId       Order ID
     * @param orderItemsMap Map of order items by order ID
     * @param productsMap   Map of products by product ID
     * @return List of OrderItemDTO
     */
    private List<OrderItemDTO> mapOrderItems(
            String orderId,
            Map<String, List<Order_Item>> orderItemsMap,
            Map<String, Products> productsMap) {

        List<Order_Item> orderItems = orderItemsMap.getOrDefault(orderId, List.of());

        return orderItems.stream()
                .map(orderItem -> mapOrderItem(orderItem, productsMap))
                .toList();
    }

    /**
     * Map a single order item to DTO
     *
     * @param orderItem   Order item entity
     * @param productsMap Map of products by product ID
     * @return OrderItemDTO
     */
    private OrderItemDTO mapOrderItem(Order_Item orderItem, Map<String, Products> productsMap) {
        Products product = productsMap.getOrDefault(orderItem.getProduct_id(), null);

        return OrderItemDTO.builder()
                .id(orderItem.getId())
                .product_id(orderItem.getProduct_id())
                .product_name(product == null ? "" : product.getName())
                .product_image(product == null || product.getMedia() == null ? ""
                        : product.getMedia().get(0).getKey())
                .product_code(orderItem.getProduct_code())
                .quantity(orderItem.getQuantity())
                .total_cost(orderItem.getTotal_cost())
                .selling_price(orderItem.getSelling_price())
                .type(orderItem.getType())
                .status(orderItem.getStatus())
                .status_id(orderItem.getStatus_id())
                .build();
    }

    /**
     * Create sheet configuration for Excel report generation
     *
     * @param orders     List of order report DTOs
     * @param orderItems List of order item report DTOs
     * @return Map of sheet names to sheet configurations
     */
    public Map<String, FileGeneratorUtil.SheetConfig<?, ?>> createReportSheetConfig(
            List<OrderReportDTO> orders,
            List<OrderItemReportsDTO> orderItems) {

        log.debug("Creating report sheet config for {} orders and {} order items",
                orders.size(), orderItems.size());

        FileGeneratorUtil.SheetConfig<OrderReportDTO, OrderProperties> orderConfig =
                new FileGeneratorUtil.SheetConfig<>(orders, OrderProperties.class);

        FileGeneratorUtil.SheetConfig<OrderItemReportsDTO, OrderItemsProperties> orderItemsConfig =
                new FileGeneratorUtil.SheetConfig<>(orderItems, OrderItemsProperties.class);

        Map<String, FileGeneratorUtil.SheetConfig<?, ?>> sheetConfigMap = Map.of(
                "Orders", orderConfig,
                "Order Items", orderItemsConfig
        );

        return sheetConfigMap;
    }

    /**
     * Create report DTOs from order entities
     *
     * @param ordersList List of order details
     * @param orderItems List of order items
     * @return Map containing order and order item report DTOs
     */
    public Map<String, Object> createReportDTOs(List<Order_Details> ordersList, List<Order_Item> orderItems) {
        log.debug("Creating report DTOs for {} orders and {} order items",
                ordersList.size(), orderItems.size());

        List<OrderReportDTO> orders = ordersList.stream()
                .map(OrderReportDTO::new)
                .toList();

        List<OrderItemReportsDTO> orderItemList = orderItems.stream()
                .map(OrderItemReportsDTO::new)
                .toList();

        return Map.of(
                "orders", orders,
                "orderItems", orderItemList
        );
    }

    /**
     * Group order items by order ID
     *
     * @param orderItems List of order items
     * @return Map of order items grouped by order ID
     */
    public Map<String, List<Order_Item>> groupOrderItemsByOrderId(List<Order_Item> orderItems) {
        return orderItems.stream()
                .collect(Collectors.groupingBy(
                        Order_Item::getOrder_id,
                        Collectors.mapping(Function.identity(), Collectors.toList())));
    }

    /**
     * Group products by ID
     *
     * @param products List of products
     * @return Map of products by ID
     */
    public Map<String, Products> groupProductsById(List<Products> products) {
        return products.stream()
                .collect(Collectors.toMap(Products::getId, product -> product));
    }
} 