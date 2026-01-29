package com.sorted.portal.service;

import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.NotifyRestockEntity;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.NotifyRestockService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.NotifyRestockStatus;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.MailBuilder;
import com.sorted.commons.notifications.EmailSenderImpl;
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
        filter.addClause(WhereClause.eq(NotifyRestockEntity.Fields.productId, product.getId()));
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
