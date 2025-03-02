package com.sorted.portal.service;

import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.Order_Item;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.service.Order_Item_Service;
import com.sorted.commons.entity.service.ProductService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class OrderService {

    private final ProductService productService;
    private final Order_Item_Service orderItemService;

    public OrderService(ProductService productService, Order_Item_Service orderItemService) {
        this.productService = productService;
        this.orderItemService = orderItemService;
    }

    @Async
    public void reduceProductQuantity(List<Products> listP, Map<String, Long> mapPQ) {
        for (Products product : listP) {
            Long quantity = mapPQ.getOrDefault(product.getId(), null);
            if (quantity == null) {
                continue;
            }
            quantity = product.getQuantity() - quantity;
            product.setQuantity(quantity);
            productService.update(product.getId(), product, Defaults.SYSTEM_ADMIN);
        }
    }

    @Async
    public void createOrderItems(List<Order_Item> listOI, String orderId, String orderCode, String cudBy) {
        for (Order_Item order_Item : listOI) {
            order_Item.setOrder_id(orderId);
            order_Item.setOrder_code(orderCode);
            orderItemService.create(order_Item, cudBy);
        }
    }
}
