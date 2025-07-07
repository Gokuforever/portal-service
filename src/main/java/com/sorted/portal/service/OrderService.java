package com.sorted.portal.service;

import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.Cart;
import com.sorted.commons.entity.mongo.Order_Item;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.service.Cart_Service;
import com.sorted.commons.entity.service.Order_Item_Service;
import com.sorted.commons.entity.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductService productService;
    private final Order_Item_Service orderItemService;
    private final Cart_Service cartService;

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
    public void emptyCart(String cartId, String cudBy) {
        Optional<Cart> cartById = cartService.findById(cartId);
        if (cartById.isEmpty()) return;
        Cart cart = cartById.get();
        cart.setCart_items(new ArrayList<>());
        cartService.update(cartId, cart, cudBy);
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
