package com.sorted.portal.service;

import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.Address;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Cart;
import com.sorted.commons.entity.service.Address_Service;
import com.sorted.commons.entity.service.Cart_Service;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.helper.AggregationFilter.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class SignUpService {

    private final Address_Service addressService;
    private final Cart_Service cartService;

    public SignUpService(Address_Service addressService, Cart_Service cartService) {
        this.addressService = addressService;
        this.cartService = cartService;
    }

    @Async
    public void migrateAddressForCustomer(String guest_user_id, String customer_user_id) {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Address.Fields.entity_id, guest_user_id));
        filter.addClause(WhereClause.eq(Address.Fields.user_type, UserType.GUEST.name()));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Address> addresses = addressService.repoFind(filter);
        if (CollectionUtils.isEmpty(addresses)) {
            return;
        }

        SEFilter filterC = new SEFilter(SEFilterType.AND);
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.user_id, customer_user_id));
        List<Address> customerAddresses = addressService.repoFind(filterC);
        if (!CollectionUtils.isEmpty(customerAddresses)) {
            return;
        }

        List<Address> addressList = addresses.stream().map(address ->
                new Address(address, UserType.CUSTOMER, customer_user_id)
        ).toList();

        addressService.bulkCreate(addressList, customer_user_id);
    }

    @Async
    public void migrateCart(String guest_user_id, String customer_user_id) {
        Cart new_cart = new Cart();
        new_cart.setUser_id(customer_user_id);
        if (StringUtils.hasText(guest_user_id)) {
            SEFilter filterC = new SEFilter(SEFilterType.AND);
            filterC.addClause(WhereClause.eq(Cart.Fields.user_id, guest_user_id));
            filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Cart cart = cartService.repoFindOne(filterC);
            if (cart != null) {
                new_cart.setCart_items(cart.getCart_items());
            }
        }

        cartService.create(new_cart, Defaults.SIGN_UP);
    }
}
