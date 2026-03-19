package com.sorted.portal.service;

import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.NotifyRestockEntity;
import com.sorted.common.entity.mongo.Products;
import com.sorted.common.entity.mongo.Users;
import com.sorted.common.entity.service.NotifyRestockService;
import com.sorted.common.entity.service.Users_Service;
import com.sorted.common.enums.NotifyRestockStatus;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.helper.MailBuilder;
import com.sorted.common.notifications.EmailSenderImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RestockNotificationService {

    private final NotifyRestockService notifyRestockService;
    private final Users_Service usersService;
    private final EmailSenderImpl emailSender;

    @Async
    public void sendNotification(Products product) {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(NotifyRestockEntity.Fields.productMasterId, product.getProduct_master_id()));
        filter.addClause(WhereClause.eq(NotifyRestockEntity.Fields.status, NotifyRestockStatus.PENDING.name()));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<NotifyRestockEntity> notifyRestockEntities = notifyRestockService.repoFind(filter);
        if (CollectionUtils.isEmpty(notifyRestockEntities)) {
            return;
        }

        List<String> userIds = notifyRestockEntities.stream().map(NotifyRestockEntity::getUserId).distinct().toList();

        SEFilter filterU = new SEFilter(SEFilterType.AND);
        filterU.addClause(WhereClause.in(BaseMongoEntity.Fields.id, userIds));
        filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        List<Users> users = usersService.repoFind(filterU);
        if (CollectionUtils.isEmpty(users)) {
            return;
        }
        Map<String, Users> usersMap = users.stream().collect(Collectors.toMap(BaseMongoEntity::getId, e -> e));

        for (NotifyRestockEntity notifyRestockEntity : notifyRestockEntities) {

            Users user = usersMap.getOrDefault(notifyRestockEntity.getUserId(), null);
            if (user == null) {
                continue;
            }

            MailBuilder mailBuilder = new MailBuilder();
            mailBuilder.setSubject("Restock Notification");
            mailBuilder.setTo(user.getEmail_id());
            mailBuilder.setTemplate(null);
            emailSender.sendEmailHtmlTemplate(mailBuilder);
        }


    }

}
