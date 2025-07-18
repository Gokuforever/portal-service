package com.sorted.portal.crons;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sorted.commons.beans.BusinessHours;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.Order_Details_Service;
import com.sorted.commons.entity.service.Seller_Service;
import com.sorted.commons.entity.service.StoreActivityService;
import com.sorted.commons.enums.*;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.porter.res.beans.FetchOrderRes;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.portal.service.order.OrderStatusCheckService;
import com.sorted.portal.service.secure.SecureReturnDataService;
import com.sorted.portal.service.secure.SecureReturnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class ManageCrons_BLService {


    private final Order_Details_Service order_Details_Service;
    private final PorterUtility porterUtility;
    private final OrderStatusCheckService orderStatusCheckService;
    private final Seller_Service seller_Service;
    private final StoreActivityService storeActivityService;
    private final SecureReturnDataService secureReturnDataService;
    private final SecureReturnService secureReturnService;

//    @Scheduled(fixedRate = 10000) // Executes every 5000ms (5 seconds)
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
        if (!details.getDp_order_id().equals(fetchOrderRes.getOrder_id())) {
            // TODO:: send mail to Studeaze team
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
        porterUtility.updateOrderStatus(details, fetchOrderRes.getStatus(), fetchOrderRes.getFare_details());
    }

    //    @Scheduled(fixedRate = 60000) // Executes every 60000ms (1 minute)
    public void phonePeStatusCheckForPendingTransactions() {
        SEFilter filterOD = new SEFilter(SEFilterType.AND);
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


    @Scheduled(cron = "0 0 9,12,15,18 * * *")
    public void initiatePickUpForSecureReturn() throws JsonProcessingException {
        TimeSlot timeSlot = TimeSlot.getCurrentTimeSlot();
        if (timeSlot == null) return;

        List<Order_Details> orderDetailsList = secureReturnDataService.fetchEligibleOrders(timeSlot);
        if (CollectionUtils.isEmpty(orderDetailsList)) return;

        Map<String, List<Order_Item>> itemsMap = secureReturnDataService.fetchOrderItemsMap(orderDetailsList);
        Map<String, Seller> sellerMap = secureReturnDataService.fetchSellerMap(orderDetailsList);
        Map<String, Users> userMap = secureReturnDataService.fetchUserMap(orderDetailsList);

        for (Order_Details order : orderDetailsList) {
            secureReturnService.process(order, itemsMap.getOrDefault(order.getId(), null),
                    sellerMap.getOrDefault(order.getSeller_id(), null),
                    userMap.getOrDefault(order.getUser_id(), null));
        }
    }

}

