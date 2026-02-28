package com.sorted.portal.bl_services;

import com.sorted.common.beans.UsersBean;
import com.sorted.common.entity.mongo.NotifyRestockEntity;
import com.sorted.common.entity.mongo.Products;
import com.sorted.common.entity.mongo.Role;
import com.sorted.common.entity.service.NotifyRestockService;
import com.sorted.common.entity.service.ProductService;
import com.sorted.common.entity.service.Users_Service;
import com.sorted.common.enums.Activity;
import com.sorted.common.enums.NotifyRestockStatus;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.AccessDeniedException;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.utils.CommonUtils;
import com.sorted.common.utils.Preconditions;
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
