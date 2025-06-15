package com.sorted.portal.crons;

import com.sorted.commons.beans.TableConfig;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Order_Details;
import com.sorted.commons.entity.mongo.Order_Item;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Order_Details_Service;
import com.sorted.commons.entity.service.Order_Item_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.ColumnType;
import com.sorted.commons.enums.MailTemplate;
import com.sorted.commons.enums.OrderStatus;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.MailBuilder;
import com.sorted.commons.notifications.EmailSenderImpl;
import com.sorted.commons.porter.res.beans.FetchOrderRes;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.commons.utils.TemplateProcessorUtil;
import com.sorted.portal.service.order.OrderStatusCheckService;
import com.sorted.portal.service.order.OrderTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ManageCrons_BLService {


    private final Order_Details_Service order_Details_Service;
    private final PorterUtility porterUtility;
    private final Order_Item_Service order_Item_Service;
    private final Users_Service usersService;
    private final EmailSenderImpl emailSenderImpl;
    private final OrderTemplateService orderTemplateService;
    private final OrderStatusCheckService orderStatusCheckService;

    //	@Scheduled(fixedRate = 5000) // Executes every 5000ms (5 seconds)
    public void porterStatusCheck() {
        SEFilter filterOD = new SEFilter(SEFilterType.AND);
        filterOD.addClause(WhereClause.notEq(Order_Details.Fields.dp_order_id, null));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterOD.addClause(
                WhereClause.lte(BaseMongoEntity.Fields.modification_date, LocalDateTime.now().minusMinutes(5)));
        filterOD.addClause(
                WhereClause.in(Order_Details.Fields.status_id, Arrays.asList(OrderStatus.READY_FOR_PICK_UP.getId(),
                        OrderStatus.RIDER_ASSIGNED.getId(), OrderStatus.OUT_FOR_DELIVERY.getId())));

        List<Order_Details> listOD = order_Details_Service.repoFind(filterOD);
        if (CollectionUtils.isEmpty(listOD)) {
            return;
        }

        listOD.parallelStream().forEach(orderDetails -> {
            try {
                updateOrderStatus(orderDetails);
            } catch (Exception e) {
                log.error("Error processing order: {}, Error: {}", orderDetails.getId(), e.getMessage());
                e.printStackTrace();
            }
        });

    }

    //	@Scheduled(fixedRate = 5000) // Executes every 5000ms (5 seconds)
    public void porterStatusCheckForCancelledOrders() {
        SEFilter filterOD = new SEFilter(SEFilterType.AND);
        filterOD.addClause(WhereClause.notEq(Order_Details.Fields.dp_order_id, null));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterOD.addClause(
                WhereClause.lte(BaseMongoEntity.Fields.modification_date, LocalDateTime.now().minusMinutes(5)));
        filterOD.addClause(
                WhereClause.gte(BaseMongoEntity.Fields.modification_date, LocalDateTime.now().minusHours(2)));
        filterOD.addClause(WhereClause.eq(Order_Details.Fields.status_id, OrderStatus.ORDER_CANCELLED.getId()));

        List<Order_Details> listOD = order_Details_Service.repoFind(filterOD);
        if (CollectionUtils.isEmpty(listOD)) {
            return;
        }

        listOD.parallelStream().forEach(orderDetails -> {
            try {
                updateOrderStatus(orderDetails);
            } catch (Exception e) {
                // Log the error with relevant details
                log.error("Error processing order: {}, Error: {}", orderDetails.getId(), e.getMessage());
                e.printStackTrace(); // For full stack trace (use a proper logging framework in production)
            }
        });
    }

    private void updateOrderStatus(Order_Details details) {
        FetchOrderRes fetchOrderRes = porterUtility.getOrder(details.getDp_order_id());
        if (details.getDp_order_id().equals(fetchOrderRes.getOrder_id())) {
            // TODO:: send mail to Studeaze team
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }

        OrderStatus currentOrderStatus = null;
        switch (fetchOrderRes.getStatus()) {
            case open:
                currentOrderStatus = OrderStatus.READY_FOR_PICK_UP;
                break;
            case accepted:
                currentOrderStatus = OrderStatus.RIDER_ASSIGNED;
                break;
            case cancelled:
                currentOrderStatus = OrderStatus.ORDER_CANCELLED;
                break;
            case ended:
                currentOrderStatus = OrderStatus.DELIVERED;

                SEFilter filterU = new SEFilter(SEFilterType.AND);
                filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, details.getUser_id()));

                Users user = usersService.repoFindOne(filterU);
                if(user == null) {
                    throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
                }
                String userName = user.getFirst_name() + " " + user.getLast_name();
                String orderTemplateTable = orderTemplateService.getOrderTemplateTable(details);

                String mailContent = userName + "|" + orderTemplateTable;

                MailBuilder builder = new MailBuilder();
                builder.setTo(user.getEmail_id());
                builder.setContent(mailContent);
                builder.setTemplate(MailTemplate.ORDER_ARRIVED);
                emailSenderImpl.sendEmailHtmlTemplate(builder);
                break;
            case live:
                currentOrderStatus = OrderStatus.OUT_FOR_DELIVERY;
                break;
            default:
                break;
        }
        if (currentOrderStatus != null && details.getStatus() != currentOrderStatus) {
            List<Order_Item> listOI = getOrderItems(details);
            final OrderStatus finalOrderStatus = currentOrderStatus;

            listOI.forEach(e -> {
                e.setStatus(finalOrderStatus, Defaults.PORTER_STCHK_CRON);
                order_Item_Service.update(e.getId(), e, Defaults.PORTER_STCHK_CRON);
            });
            details.setFare_details(fetchOrderRes.getFare_details());
            details.setStatus(finalOrderStatus, Defaults.PORTER_STCHK_CRON);
            order_Details_Service.update(details.getId(), details, Defaults.PORTER_STCHK_CRON);
        }
    }

    @NotNull
    private List<Order_Item> getOrderItems(Order_Details details) {
        SEFilter filterOI = new SEFilter(SEFilterType.AND);
        filterOI.addClause(WhereClause.eq(Order_Item.Fields.order_id, details.getId()));
        filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Order_Item> listOI = order_Item_Service.repoFind(filterOI);
        if (CollectionUtils.isEmpty(listOI)) {
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }
        return listOI;
    }

    //	@Scheduled(fixedRate = 5000) // Executes every 5000ms (5 seconds)
    public void phonePeStatusCheckForPendingTransactions() {
        SEFilter filterOD = new SEFilter(SEFilterType.AND);
        filterOD.addClause(WhereClause.notEq(Order_Details.Fields.dp_order_id, null));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filterOD.addClause(
                WhereClause.lte(BaseMongoEntity.Fields.modification_date, LocalDateTime.now().minusMinutes(5)));
        filterOD.addClause(
                WhereClause.in(Order_Details.Fields.status_id, Arrays.asList(OrderStatus.TRANSACTION_PENDING.getId(),
                        OrderStatus.ORDER_PLACED.getId())));

        List<Order_Details> listOD = order_Details_Service.repoFind(filterOD);
        if (CollectionUtils.isEmpty(listOD)) {
            return;
        }
        listOD.parallelStream().forEach(orderStatusCheckService::checkOrderStatus);
    }
}

