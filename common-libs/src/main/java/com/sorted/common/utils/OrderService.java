package com.sorted.common.utils;

import com.sorted.common.beans.CartItems;
import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.mongo.*;
import com.sorted.common.entity.service.Cart_Service;
import com.sorted.common.entity.service.Order_Item_Service;
import com.sorted.common.entity.service.ProductService;
import com.sorted.common.enums.All_Status;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductService productService;
    private final Order_Item_Service orderItemService;
    private final Cart_Service cartService;
    private final ComboUtility comboUtility;

    public void increaseProductQuantity(Products product, Long quantity) {
        quantity = product.getQuantity() + quantity;
        product.setQuantity(quantity);
        productService.update(product.getId(), product, Defaults.SYSTEM_ADMIN);
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
    public void reduceProductQuantity(List<CartItems> cartItems) {

        Map<String, List<String>> comboProductIds = new HashMap<>();
        List<String> productIds = new ArrayList<>(cartItems.stream().filter(e -> e.getCurrent_status().equals(All_Status.ProductCurrentStatus.IN_STOCK.getStatus_id()) && !e.is_combo()).map(CartItems::getProduct_id).toList());
        List<String> comboIds = cartItems.stream().filter(e -> e.getCurrent_status().equals(All_Status.ProductCurrentStatus.IN_STOCK.getStatus_id()) && e.is_combo()).map(CartItems::getProduct_id).toList();
        List<Combo> activeCombos = comboUtility.getActiveCombos(comboIds);
        boolean containsCombo = !CollectionUtils.isEmpty(activeCombos);
        if (containsCombo) {
            comboProductIds = activeCombos.stream().collect(Collectors.toMap(Combo::getId, Combo::getItem_ids));
            for (Combo activeCombo : activeCombos) {
                productIds.addAll(activeCombo.getItem_ids());
            }
        }

        SEFilter filterP = new SEFilter(SEFilterType.AND);
        filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, productIds));
        filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Products> listP = productService.repoFind(filterP);
        if (CollectionUtils.isEmpty(listP)) {
            return;
        }

        Map<String, Products> productsMap = listP.stream().collect(Collectors.toMap(Products::getId, p -> p));

        for (CartItems cartItem : cartItems) {
            if (containsCombo && comboProductIds.containsKey(cartItem.getProduct_id())) {
                for (String productId : comboProductIds.get(cartItem.getProduct_id())) {
                    reduceProductQuantity(productId, cartItem.getQuantity(), cartItem.getCurrent_status(), productsMap);
                }
                continue;
            }
            reduceProductQuantity(cartItem.getProduct_id(), cartItem.getQuantity(), cartItem.getCurrent_status(), productsMap);
        }
    }

    private void reduceProductQuantity(String productId, long quantity, int currentStatus, Map<String, Products> productsMap) {
        Products products = productsMap.getOrDefault(productId, null);
        if (products == null) {
            return;
        }
        if (currentStatus == All_Status.ProductCurrentStatus.IN_STOCK.getStatus_id()) {
            return;
        }
        quantity -= products.getQuantity();
        products.setQuantity(quantity);
        productService.update(products.getId(), products, Defaults.SYSTEM_ADMIN);
    }

    @Async
    public void emptyCart(String cartId, String cudBy) {
        Optional<Cart> cartById = cartService.findById(cartId);
        if (cartById.isEmpty()) return;
        Cart cart = cartById.get();
        cart.setCart_items(new ArrayList<>());
        cart.setCouponCode(null);
        cart.setDelivery_charges(0L);
        cart.setSmall_cart_fee(0L);
        cart.setHandling_charges(0L);
        cart.setTotal_price(BigDecimal.ZERO);
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