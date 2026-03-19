package com.sorted.portal.bl_services;

import com.sorted.common.beans.UsersBean;
import com.sorted.common.entity.mongo.NotifyRestockEntity;
import com.sorted.common.entity.mongo.Product_Master;
import com.sorted.common.entity.mongo.Role;
import com.sorted.common.entity.service.NotifyRestockService;
import com.sorted.common.entity.service.ProductService;
import com.sorted.common.entity.service.Product_Master_Service;
import com.sorted.common.entity.service.Users_Service;
import com.sorted.common.enums.Activity;
import com.sorted.common.enums.NotifyRestockStatus;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.enums.UserType;
import com.sorted.common.exceptions.AccessDeniedException;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.utils.CommonUtils;
import com.sorted.portal.request.beans.RestockNotificationReq;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@RestController
@RequestMapping("/restock-notifications")
public class ManageRestockNotifications_BLService {

    private final Users_Service usersService;
    private final NotifyRestockService notifyRestockService;
    private final ProductService productService;
    private final Product_Master_Service productMasterService;

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

        Optional<Product_Master> optionalProductMaster = productMasterService.findById(request.getProductMasterId());
        if (optionalProductMaster.isEmpty()) {
            throw new CustomIllegalArgumentsException(ResponseCode.PRODUCT_NOT_FOUND);
        }
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(NotifyRestockEntity.Fields.userId, request.getReq_user_id()));
        filter.addClause(WhereClause.eq(NotifyRestockEntity.Fields.productId, request.getProductMasterId()));
        filter.addClause(WhereClause.eq(NotifyRestockEntity.Fields.status, NotifyRestockStatus.PENDING.name()));

        long count = notifyRestockService.countByFilter(filter);
        if (count > 0) {
            return;
        }

        NotifyRestockEntity entity = NotifyRestockEntity.builder()
                .productMasterId(request.getProductMasterId())
                .userId(request.getReq_user_id())
                .status(NotifyRestockStatus.PENDING)
                .read(false)
                .build();

        notifyRestockService.create(entity, request.getReq_user_id());
    }

    @GetMapping("/list")
    public List<NotifyRestockEntity> list(HttpServletRequest httpServletRequest) {
        String req_user_id = httpServletRequest.getHeader("req_user_id");
        UsersBean usersBean = usersService.validateUserForActivity(req_user_id, Activity.INVENTORY_MANAGEMENT);
        if (usersBean.getRole().getUser_type() != UserType.SELLER){
            throw new AccessDeniedException();
        }

        return null;
    }
}
