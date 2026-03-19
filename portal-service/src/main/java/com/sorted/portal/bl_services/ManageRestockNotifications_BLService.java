package com.sorted.portal.bl_services;

import com.sorted.common.beans.UsersBean;
import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.NotifyRestockEntity;
import com.sorted.common.entity.mongo.Product_Master;
import com.sorted.common.entity.mongo.Role;
import com.sorted.common.entity.service.NotifyRestockService;
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
import com.sorted.portal.request.beans.MarkNotificationsReadReq;
import com.sorted.portal.request.beans.RestockNotificationReq;
import com.sorted.portal.response.beans.RestockResponseBean;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
@RequestMapping("/restock-notifications")
public class ManageRestockNotifications_BLService {

    private final Users_Service usersService;
    private final NotifyRestockService notifyRestockService;
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
        filter.addClause(WhereClause.eq(NotifyRestockEntity.Fields.productMasterId, request.getProductMasterId()));
        filter.addClause(WhereClause.eq(NotifyRestockEntity.Fields.status, NotifyRestockStatus.PENDING.name()));

        long count = notifyRestockService.countByFilter(filter);
        if (count > 0) {
            return;
        }

        NotifyRestockEntity entity = NotifyRestockEntity.builder()
                .productMasterId(request.getProductMasterId())
                .userId(request.getReq_user_id())
                .status(NotifyRestockStatus.PENDING)
                .zoneId(usersBean.getNearestZoneId())
                .read(false)
                .build();

        notifyRestockService.create(entity, request.getReq_user_id());
    }

    @GetMapping("/list")
    public List<RestockResponseBean> list(HttpServletRequest httpServletRequest) {
        String req_user_id = httpServletRequest.getHeader("req_user_id");
        UsersBean usersBean = usersService.validateUserForActivity(req_user_id, Activity.INVENTORY_MANAGEMENT);
        if (usersBean.getRole().getUser_type() != UserType.SELLER) {
            throw new AccessDeniedException();
        }

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.in(NotifyRestockEntity.Fields.zoneId, usersBean.getSeller().getDeliverableZones()));
        filter.addClause(WhereClause.eq(NotifyRestockEntity.Fields.status, NotifyRestockStatus.PENDING.name()));

        List<NotifyRestockEntity> notifyRestockEntities = notifyRestockService.repoFind(filter);

        if (CollectionUtils.isEmpty(notifyRestockEntities)) {
            return Collections.emptyList();
        }

        List<String> productMasterIds = notifyRestockEntities.stream().map(NotifyRestockEntity::getProductMasterId).toList();

        SEFilter filterPM = new SEFilter(SEFilterType.AND);
        filterPM.addClause(WhereClause.in(BaseMongoEntity.Fields.id, productMasterIds));
        filterPM.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Product_Master> productMasters = productMasterService.repoFind(filterPM);

        Map<String, Product_Master> productMasterMap = productMasters.stream().collect(Collectors.toMap(BaseMongoEntity::getId, p -> p));

        return notifyRestockEntities.stream().map(r -> mapToResDTO(r, productMasterMap)).toList();
    }

    @PutMapping("/mark-read")
    public void markAsRead(@RequestBody MarkNotificationsReadReq request, HttpServletRequest httpServletRequest) {
        CommonUtils.extractHeaders(httpServletRequest, request);
        UsersBean usersBean = usersService.validateUserForActivity(request.getReq_user_id(), Activity.INVENTORY_MANAGEMENT);
        if (usersBean.getRole().getUser_type() != UserType.SELLER) {
            throw new AccessDeniedException();
        }

        if (CollectionUtils.isEmpty(request.getNotificationIds())) {
            throw new CustomIllegalArgumentsException("Notification IDs are required");
        }

        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.in(BaseMongoEntity.Fields.id, request.getNotificationIds()));
        filter.addClause(WhereClause.in(NotifyRestockEntity.Fields.zoneId, usersBean.getSeller().getDeliverableZones()));
        filter.addClause(WhereClause.eq(NotifyRestockEntity.Fields.status, NotifyRestockStatus.PENDING.name()));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<NotifyRestockEntity> notifications = notifyRestockService.repoFind(filter);

        if (CollectionUtils.isEmpty(notifications)) {
            return;
        }

        for (NotifyRestockEntity notification : notifications) {
            notification.setRead(true);
            notifyRestockService.update(notification.getId(), notification, request.getReq_user_id());
        }
    }
    

    private static RestockResponseBean mapToResDTO(NotifyRestockEntity notifyRestockEntity, Map<String, Product_Master> productMasterMap) {

        Product_Master productMaster = productMasterMap.getOrDefault(notifyRestockEntity.getProductMasterId(), null);
        if (productMaster == null) {
            return null;
        }
        return RestockResponseBean.builder()
                .cdnUrl(productMaster.getCdn_url())
                .productMasterId(notifyRestockEntity.getProductMasterId())
                .restockRequestOn(notifyRestockEntity.getCreation_date_str())
                .productName(productMaster.getName())
                .read(notifyRestockEntity.isRead())
                .build();
    }
}
