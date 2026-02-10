package com.sorted.portal.crons;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.phonepe.sdk.pg.common.models.response.RefundStatusResponse;
import com.sorted.commons.beans.BusinessHours;
import com.sorted.commons.beans.Spoc_Details;
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
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.GcpStorageService;
import com.sorted.commons.utils.InternalMailService;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.portal.PhonePe.PhonePeUtility;
import com.sorted.portal.response.beans.OrderItemReportBean;
import com.sorted.portal.response.beans.OrderReportBean;
import com.sorted.portal.service.order.OrderStatusCheckService;
import com.sorted.portal.service.order.OrderTemplateService;
import com.sorted.portal.service.secure.SecureReturnDataService;
import com.sorted.portal.service.secure.SecureReturnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.InvalidParameterException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class ManageCrons_BLService {

    private final OrderTemplateService orderTemplateService;
    private final Order_Details_Service order_Details_Service;
    private final Order_Item_Service orderItemService;
    private final PorterUtility porterUtility;
    private final OrderStatusCheckService orderStatusCheckService;
    private final Seller_Service seller_Service;
    private final StoreActivityService storeActivityService;
    private final SecureReturnDataService secureReturnDataService;
    private final SecureReturnService secureReturnService;
    private final Users_Service usersService;
    private final EmailSenderImpl emailSenderImpl;
    private final InternalMailService internalMailService;
    private final PhonePeUtility phonePeUtility;
    private final GcpStorageService gcpStorageService;

    @Scheduled(fixedRate = 60000) // Executes every 5000ms (5 seconds)
    public void porterStatusCheck() {
        SEFilter filterOD = new SEFilter(SEFilterType.AND);
        filterOD.addClause(WhereClause.notEq(Order_Details.Fields.dp_order_id, null));
        filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
//        filterOD.addClause(
//                WhereClause.lte(BaseMongoEntity.Fields.modification_date, LocalDateTime.now().minusMinutes(5)));
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

    @Scheduled(fixedRate = 60000) // Executes every 5000ms (5 seconds)
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
        FetchOrderRes fetchOrderRes = porterUtility.getOrderStatus(details.getDp_order_id());
        if (!details.getDp_order_id().equals(fetchOrderRes.getOrder_id())) {
            internalMailService.sendMailOnError("Order id mismatch from porter.", details.getDp_order_id(), new InvalidParameterException("Order id mismatch from porter."));
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
        porterUtility.updateOrderStatus(details, fetchOrderRes);
    }

    @Scheduled(fixedRate = 60000) // Executes every 60000ms (1 minute)
    public void phonePeStatusCheckForPendingTransactions() {
        log.info("PhonePe Status Check For Pending Transactions");
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


    //    @Scheduled(cron = "0 0 9,12,15,18 * * *")
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

    @Scheduled(cron = "0 0 10 * * ?")
    public void sendReminderToSellers() {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(WhereClause.eq(Order_Details.Fields.status_id, OrderStatus.STORE_NOT_OPERATIONAL.getId()));

        List<Order_Details> orderDetails = order_Details_Service.repoFind(filter);

        if (CollectionUtils.isEmpty(orderDetails)) {
            return;
        }
        List<String> userIds = orderDetails.stream().map(Order_Details::getUser_id).distinct().toList();

        SEFilter filterU = new SEFilter(SEFilterType.AND);
        filterU.addClause(WhereClause.in(BaseMongoEntity.Fields.id, userIds));
        filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Users> users = usersService.repoFind(filterU);
        Map<String, Users> usersMap = users.stream().collect(Collectors.toMap(Users::getId, u -> u));

        List<String> sellerIds = orderDetails.stream().map(Order_Details::getSeller_id).distinct().toList();

        SEFilter filterS = new SEFilter(SEFilterType.AND);
        filterS.addClause(WhereClause.in(BaseMongoEntity.Fields.id, sellerIds));
        filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Seller> sellers = seller_Service.repoFind(filterS);
        Map<String, Seller> sellerMap = sellers.stream().collect(Collectors.toMap(Seller::getId, s -> s));


        for (Order_Details order : orderDetails) {
            Users user = usersMap.getOrDefault(order.getUser_id(), null);
            Seller seller = sellerMap.getOrDefault(order.getSeller_id(), null);
            if (user == null || seller == null) {
                continue;
            }
            String orderTemplateTable = orderTemplateService.getOrderTemplateTable(order);
            Optional<Spoc_Details> first = seller.getSpoc_details().stream().filter(Spoc_Details::isPrimary).findFirst();
            if (first.isPresent()) {
                Spoc_Details spocDetails = first.get();
                String mailId = spocDetails.getEmail_id();
                String firstName = spocDetails.getFirst_name();
                String mailContent = firstName + "|" + orderTemplateTable;
                MailBuilder mailBuilder = new MailBuilder();
                mailBuilder.setTo(mailId);
                mailBuilder.setContent(mailContent);
                mailBuilder.setTemplate(MailTemplate.NEW_ORDER_ARRIVED);
                emailSenderImpl.sendEmailHtmlTemplate(mailBuilder);
            }
        }
    }


    @Scheduled(fixedRate = 60000)
    public void checkPhonePeRefundStatus() {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(WhereClause.eq(Order_Details.Fields.status_id, OrderStatus.PENDING_REFUND.getId()));

        List<Order_Details> orderDetails = order_Details_Service.repoFind(filter);

        if (CollectionUtils.isEmpty(orderDetails)) {
            return;
        }

        for (Order_Details order : orderDetails) {
            Optional<RefundStatusResponse> refundStatusResponse = phonePeUtility.refundStatus(order.getRefund_transaction_id());
            if (refundStatusResponse.isEmpty()) {
                return;
            }

            RefundStatusResponse response = refundStatusResponse.get();
            if (response.getState().equals("COMPLETED")) {
                order.setStatus(OrderStatus.FULLY_REFUNDED, Defaults.PHONEPE_REFUND_CRON);
                order_Details_Service.update(order.getId(), order, Defaults.PHONEPE_REFUND_CRON);
            } else if (response.getState().equals("FAILED")) {
                order.setStatus(OrderStatus.REFUND_FAILED, Defaults.PHONEPE_REFUND_CRON);
                order_Details_Service.update(order.getId(), order, Defaults.PHONEPE_REFUND_CRON);
                internalMailService.sendMailOnError("Refund failed for order ID: " + order.getId(), "Refund failed for order ID: " + order.getId(), null);
            }
        }
    }

    public void generateOrderReport() {
        LocalDate now = LocalDate.now();
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(WhereClause.eq(Order_Details.Fields.status_id, OrderStatus.DELIVERED.getId()));
//        filter.addClause(WhereClause.lt(BaseMongoEntity.Fields.creation_date, now.plusDays(1).atStartOfDay()));
//        filter.addClause(WhereClause.gt(BaseMongoEntity.Fields.creation_date, now.plusDays(7).atStartOfDay()));

        List<Order_Details> orderDetails = order_Details_Service.repoFind(filter);

        if (CollectionUtils.isEmpty(orderDetails)) {
            return;
        }
        List<String> orderIds = orderDetails.stream().map(Order_Details::getId).toList();

        SEFilter filterOI = new SEFilter(SEFilterType.AND);
        filterOI.addClause(WhereClause.in(Order_Item.Fields.order_id, orderIds));
        filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Order_Item> orderItems = orderItemService.repoFind(filterOI);

        Map<String, List<Order_Item>> orderItemMap = orderItems.stream().collect(Collectors.groupingBy(Order_Item::getOrder_id));

        List<OrderReportBean> orderReportBeans = new ArrayList<>();
        for (Order_Details orderDetail : orderDetails) {
            List<Order_Item> orderItemList = orderItemMap.getOrDefault(orderDetail.getId(), null);
            if (orderItemList == null) {
                continue;
            }
            List<OrderItemReportBean> orderItemReportBeans = new ArrayList<>();
            for (Order_Item orderItem : orderItemList) {
                orderItemReportBeans.add(OrderItemReportBean.builder()
                        .name(orderItem.isCombo() ? orderItem.getCombo_name() : orderItem.getProduct_name())
                        .quantity(orderItem.getQuantity().intValue())
                        .price(CommonUtils.paiseToRupee(orderItem.getSelling_price() * 90 / 100))
                        .total(CommonUtils.paiseToRupee(orderItem.getTotal_cost() * 90 / 100))
                        .build());
            }
            OrderReportBean orderReportBean = OrderReportBean.builder()
                    .orderId(orderDetail.getCode())
                    .orderDate(orderDetail.getCreation_date())
                    .orderAmount(orderDetail.getTotal_items_cost() == null ? BigDecimal.ZERO : CommonUtils.paiseToRupee(orderDetail.getTotal_items_cost() * 90 / 100))
                    .orderQuantity(orderItemList.stream().map(Order_Item::getQuantity).reduce(0L, Long::sum).intValue())
                    .orderItems(orderItemReportBeans)
                    .build();
            orderReportBeans.add(orderReportBean);
        }

        try {
            // Generate Excel file in memory
            byte[] excelBytes = generateExcelBytes(orderReportBeans);

            // Upload to S3
            String fileName = "Order_Report_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            String s3Url = gcpStorageService.uploadExcelReport(excelBytes, fileName);

            log.info("Order report generated and uploaded successfully: {}", s3Url);

        } catch (IOException e) {
            log.error("Failed to generate or upload order report", e);
            throw new RuntimeException("Failed to generate order report", e);
        }
    }

    private byte[] generateExcelBytes(List<OrderReportBean> orderReportBeans) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Order Report");

            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Create data style
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // Create currency style
            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.cloneStyleFrom(dataStyle);
            currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("₹#,##0.00"));

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Order ID", "Order Date", "Order Quantity", "Order Amount",
                    "Item Name", "Item Quantity", "Item Price", "Item Total"};

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Fill data
            int rowNum = 1;
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

            for (OrderReportBean order : orderReportBeans) {
                List<OrderItemReportBean> items = order.getOrderItems();

                for (int i = 0; i < items.size(); i++) {
                    Row row = sheet.createRow(rowNum++);
                    OrderItemReportBean item = items.get(i);

                    // Order details (only on first item row)
                    if (i == 0) {
                        Cell orderIdCell = row.createCell(0);
                        orderIdCell.setCellValue(order.getOrderId());
                        orderIdCell.setCellStyle(dataStyle);

                        Cell orderDateCell = row.createCell(1);
                        orderDateCell.setCellValue(order.getOrderDate().format(dateFormatter));
                        orderDateCell.setCellStyle(dataStyle);

                        Cell orderQtyCell = row.createCell(2);
                        orderQtyCell.setCellValue(order.getOrderQuantity());
                        orderQtyCell.setCellStyle(dataStyle);

                        Cell amountCell = row.createCell(3);
                        amountCell.setCellValue(order.getOrderAmount().doubleValue());
                        amountCell.setCellStyle(currencyStyle);
                    } else {
                        // Empty cells for subsequent items of same order
                        for (int j = 0; j < 4; j++) {
                            Cell emptyCell = row.createCell(j);
                            emptyCell.setCellValue("");
                            emptyCell.setCellStyle(dataStyle);
                        }
                    }

                    // Item details
                    Cell itemNameCell = row.createCell(4);
                    itemNameCell.setCellValue(item.getName());
                    itemNameCell.setCellStyle(dataStyle);

                    Cell itemQtyCell = row.createCell(5);
                    itemQtyCell.setCellValue(item.getQuantity());
                    itemQtyCell.setCellStyle(dataStyle);

                    Cell priceCell = row.createCell(6);
                    priceCell.setCellValue(item.getPrice().doubleValue());
                    priceCell.setCellStyle(currencyStyle);

                    Cell totalCell = row.createCell(7);
                    totalCell.setCellValue(item.getTotal().doubleValue());
                    totalCell.setCellStyle(currencyStyle);
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write workbook to byte array
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}