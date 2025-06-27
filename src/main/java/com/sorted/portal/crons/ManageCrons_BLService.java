package com.sorted.portal.crons;

import com.sorted.commons.beans.BusinessHours;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.*;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.MailBuilder;
import com.sorted.commons.notifications.EmailSenderImpl;
import com.sorted.commons.porter.res.beans.FetchOrderRes;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.portal.service.order.OrderStatusCheckService;
import com.sorted.portal.service.order.OrderTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
    private final Seller_Service seller_Service;
    private final StoreActivityService storeActivityService;

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
        porterUtility.updateOrderStatus(details, fetchOrderRes.getStatus(), fetchOrderRes.getFare_details());
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

    /* <<<<<<<<<<<<<<  ✨ Windsurf Command 🌟 >>>>>>>>>>>>>>>> */
//    @Scheduled(cron = "0 */15 * * * *")
    public void evaluateStoreOpenClose() {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(WhereClause.eq(Seller.Fields.status, All_Status.Seller_Status.ACTIVE));

        List<Seller> sellers = seller_Service.repoFind(filter);
        if (CollectionUtils.isEmpty(sellers)) {
            log.info("No active sellers found");
            return;
        }

        int currentHour = getCurrentHourInIST();
        WeekDay currentDay = getCurrentDayInIST();

        log.info("Current hour in IST: {}", currentHour);
        log.info("Current day in IST: {}", currentDay);

        sellers.parallelStream().forEach(seller -> {
            log.info("Evaluating store open/close for seller: {}", seller.getBusiness_name());
            BusinessHours bh = seller.getBusiness_hours();
            if (bh == null) {
                log.info("No business hours found for seller: {}", seller.getBusiness_name());
                return;
            }

            List<WeekDay> offDays = bh.getFixed_off_days();
            if (offDays != null && offDays.contains(currentDay)) {
                log.info("Seller is closed for the day: {}", seller.getBusiness_name());
                storeActivityService.autoOpenStore(seller.getId(), Defaults.CLOSE_STORE_CRON);
                return;
            }

            Integer start = bh.getStart_time();
            Integer end = bh.getEnd_time();

            if (start == null || end == null) {
                log.info("No valid start/end time found for seller: {}", seller.getBusiness_name());
                storeActivityService.autoOpenStore(seller.getId(), Defaults.CLOSE_STORE_CRON);
                return;
            }

            log.info("Evaluating store open/close for seller: {}: start={}, end={}, currentHour={}", seller.getBusiness_name(), start, end, currentHour);

            if (currentHour >= start && currentHour < end) {
                log.info("Store is open for seller: {}", seller.getBusiness_name());
                storeActivityService.autoOpenStore(seller.getId(), Defaults.OPEN_STORE_CRON);
            } else {
                log.info("Store is closed for seller: {}", seller.getBusiness_name());
                storeActivityService.autoOpenStore(seller.getId(), Defaults.CLOSE_STORE_CRON);
            }
        });
    }
    /* <<<<<<<<<<  d435c0d4-d62c-4b61-8842-ee73abdf1c65  >>>>>>>>>>> */

    private int getCurrentHourInIST() {
        return ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).getHour();
    }

    private WeekDay getCurrentDayInIST() {
        DayOfWeek day = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).getDayOfWeek();
        return WeekDay.values()[day.getValue()];
    }


}

