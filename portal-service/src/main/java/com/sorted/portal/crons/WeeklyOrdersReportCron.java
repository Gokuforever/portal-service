package com.sorted.portal.crons;

import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Order_Details;
import com.sorted.common.entity.mongo.Order_Item;
import com.sorted.common.entity.service.Order_Details_Service;
import com.sorted.common.entity.service.Order_Item_Service;
import com.sorted.common.enums.OrderStatus;
import com.sorted.common.helper.AggregationFilter;
import com.sorted.common.utils.CommonUtils;
import com.sorted.common.utils.GcpStorageService;
import com.sorted.portal.response.beans.OrderItemReportBean;
import com.sorted.portal.response.beans.OrderReportBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

//@Component
@Slf4j
@RequiredArgsConstructor
public class WeeklyOrdersReportCron {

    private final GcpStorageService gcpStorageService;
    private final Order_Details_Service order_Details_Service;
    private final Order_Item_Service orderItemService;


    @Scheduled(fixedRate = 60000)
    public void generateOrderReport() {
        LocalDate now = LocalDate.now();
        AggregationFilter.SEFilter filter = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filter.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        filter.addClause(AggregationFilter.WhereClause.eq(Order_Details.Fields.status_id, OrderStatus.DELIVERED.getId()));
//        filter.addClause(WhereClause.lt(BaseMongoEntity.Fields.creation_date, now.plusDays(1).atStartOfDay()));
//        filter.addClause(WhereClause.gt(BaseMongoEntity.Fields.creation_date, now.plusDays(7).atStartOfDay()));

        List<Order_Details> orderDetails = order_Details_Service.repoFind(filter);

        if (CollectionUtils.isEmpty(orderDetails)) {
            return;
        }
        List<String> orderIds = orderDetails.stream().map(Order_Details::getId).toList();

        AggregationFilter.SEFilter filterOI = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filterOI.addClause(AggregationFilter.WhereClause.in(Order_Item.Fields.order_id, orderIds));
        filterOI.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

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
                if (!orderItem.getStatus().equals(OrderStatus.DELIVERED)) {
                    continue;
                }
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
                    .orderAmount(CommonUtils.paiseToRupee(orderDetail.getTotal_items_cost() * 90 / 100))
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
