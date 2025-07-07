package com.sorted.portal.service.secure;

import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.Order_Details_Service;
import com.sorted.commons.entity.service.Order_Item_Service;
import com.sorted.commons.entity.service.Seller_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.OrderStatus;
import com.sorted.commons.enums.TimeSlot;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SecureReturnDataService {

    private final Order_Details_Service orderDetailsService;
    private final Order_Item_Service orderItemService;
    private final Seller_Service sellerService;
    private final Users_Service usersService;

    public List<Order_Details> fetchEligibleOrders(TimeSlot timeSlot) {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(WhereClause.eq(Order_Details.Fields.secured_time_slot, timeSlot.name()));
        filter.addClause(WhereClause.eq(Order_Details.Fields.status_id, OrderStatus.SECURE_RETURN_INITIATED.getId()));
        return orderDetailsService.repoFind(filter);
    }

    public Map<String, List<Order_Item>> fetchOrderItemsMap(List<Order_Details> orders) {
        List<String> orderIds = orders.stream().map(Order_Details::getId).toList();
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(WhereClause.in(BaseMongoEntity.Fields.id, orderIds));
        filter.addClause(WhereClause.eq(Order_Item.Fields.status_id, OrderStatus.SECURE_RETURN_INITIATED.getId()));
        List<Order_Item> items = orderItemService.repoFind(filter);
        return items.stream().collect(Collectors.groupingBy(Order_Item::getOrder_id));
    }

    public Map<String, Seller> fetchSellerMap(List<Order_Details> orders) {
        List<String> sellerIds = orders.stream().map(Order_Details::getSeller_id).distinct().toList();
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.in(BaseMongoEntity.Fields.id, sellerIds));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Seller> sellers = sellerService.repoFind(filter);
        return sellers.stream().collect(Collectors.toMap(Seller::getId, Function.identity()));
    }

    public Map<String, Users> fetchUserMap(List<Order_Details> orders) {
        List<String> userIds = orders.stream().map(Order_Details::getUser_id).distinct().toList();
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.in(BaseMongoEntity.Fields.id, userIds));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Users> users = usersService.repoFind(filter);
        return users.stream().collect(Collectors.toMap(Users::getId, Function.identity()));
    }
}

