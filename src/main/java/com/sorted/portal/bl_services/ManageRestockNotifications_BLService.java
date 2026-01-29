package com.sorted.portal.bl_services;

import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.NotifyRestockEntity;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.service.NotifyRestockService;
import com.sorted.commons.entity.service.ProductService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.NotifyRestockStatus;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.AccessDeniedException;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.Preconditions;
import com.sorted.portal.request.beans.RestockNotificationReq;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/restock-notifications")
public class ManageRestockNotifications_BLService {

    private final Users_Service usersService;
    private final NotifyRestockService notifyRestockService;
    private final ProductService productService;

    @PostMapping("/subscribe")
    public void subscribe(@RequestBody RestockNotificationReq request, HttpServletRequest httpServletRequest) {
        CommonUtils.extractHeaders(httpServletRequest, request);
        UsersBean usersBean = usersService.validateUserForActivity(request.getReq_user_id(), Activity.PRODUCTS);
        Role role = usersBean.getRole();
        switch (role.getUser_type()) {
            case CUSTOMER:
                break;
            case GUEST:
                throw new CustomIllegalArgumentsException("Please sign up or login to receive notifications.");
            default:
                throw new AccessDeniedException();
        }

        Preconditions.check(StringUtils.hasText(request.getProductId()), ResponseCode.MISSING_PRODUCT_ID);

        Optional<Products> productsOptional = productService.findById(request.getProductId());
        if (productsOptional.isEmpty()) {
            throw new CustomIllegalArgumentsException(ResponseCode.PRODUCT_NOT_FOUND);
        }

        Long quantity = productsOptional.get().getQuantity();
        Preconditions.check(quantity == 0, ResponseCode.PRODUCT_IN_STOCK);

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(NotifyRestockEntity.Fields.userId, request.getReq_user_id()));
        filter.addClause(WhereClause.eq(NotifyRestockEntity.Fields.productId, request.getProductId()));
        filter.addClause(WhereClause.eq(NotifyRestockEntity.Fields.status, NotifyRestockStatus.PENDING.name()));

        long count = notifyRestockService.countByFilter(filter);
        if (count > 0) {
            return;
        }
        NotifyRestockEntity entity = NotifyRestockEntity.builder()
                .productId(request.getProductId())
                .userId(request.getReq_user_id())
                .status(NotifyRestockStatus.PENDING)
                .build();

        notifyRestockService.create(entity, request.getReq_user_id());
    }
}
