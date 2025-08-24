package com.sorted.portal.bl_services;

import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Order_Details;
import com.sorted.commons.entity.mongo.Order_Item;
import com.sorted.commons.entity.service.Order_Details_Service;
import com.sorted.commons.entity.service.Order_Item_Service;
import com.sorted.commons.entity.service.StoreActivityService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.OrderStatus;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.*;
import com.sorted.commons.helper.SEResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class ManageStoreActivity_BLService {

    private final StoreActivityService storeActivityService;
    private final Order_Details_Service orderDetailsService;
    private final Order_Item_Service orderItemService;
    private final Users_Service users_Service;

    @PostMapping("/store/open")
    public SEResponse openStore(HttpServletRequest request) {
        UsersBean usersBean = validateUserForActivity(request, Activity.OPEN_STORE);
        storeActivityService.openStore(usersBean.getSeller().getId(), usersBean.getId());
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(Order_Details.Fields.seller_id, usersBean.getSeller().getId()));
        filter.addClause(WhereClause.eq(Order_Details.Fields.status_id, OrderStatus.STORE_NOT_OPERATIONAL.getId()));

        List<Order_Details> orderDetails = orderDetailsService.repoFind(filter);
        if (!CollectionUtils.isEmpty(orderDetails)) {

            List<String> orderIds = orderDetails.stream().map(BaseMongoEntity::getId).collect(Collectors.toList());

            SEFilter filterOI = new SEFilter(SEFilterType.AND);
            filterOI.addClause(WhereClause.in(Order_Item.Fields.order_id, orderIds));

            List<Order_Item> orderItems = orderItemService.repoFind(filterOI);
            Map<String, List<Order_Item>> orderItemMap = orderItems.stream().collect(Collectors.groupingBy(Order_Item::getOrder_id));

            for (Order_Details orderDetail : orderDetails) {
                orderDetail.setStatus(OrderStatus.TRANSACTION_PROCESSED, usersBean.getId());
                orderDetailsService.update(orderDetail.getId(), orderDetail, usersBean.getId());

                List<Order_Item> orderItemList = orderItemMap.get(orderDetail.getId());
                for (Order_Item orderItem : orderItemList) {
                    orderItem.setStatus(OrderStatus.TRANSACTION_PROCESSED, usersBean.getId());
                    orderItemService.update(orderItem.getId(), orderItem, usersBean.getId());
                }
            }
        }
        return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
    }

    @PostMapping("/store/close")
    public SEResponse openClose(HttpServletRequest request) {
        UsersBean usersBean = validateUserForActivity(request, Activity.CLOSE_STORE);
        storeActivityService.closeStore(usersBean.getSeller().getId(), usersBean.getId());
        return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
    }

    @GetMapping("/store/status")
    public boolean getStoreStatus(HttpServletRequest request) {
        UsersBean usersBean = validateUserForActivity(request, Activity.USER_PROFILE);
        return storeActivityService.isStoreOperational(usersBean.getSeller().getId());
    }

    @NotNull
    private UsersBean validateUserForActivity(HttpServletRequest request, Activity activity) {
        String req_user_id = request.getHeader("req_user_id");
        if (!StringUtils.hasText(req_user_id)) {
            throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }
        UsersBean usersBean = users_Service.validateUserForActivity(req_user_id, activity);
        if (usersBean.getRole().getUser_type() != UserType.SELLER) {
            throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }
        return usersBean;
    }
}
