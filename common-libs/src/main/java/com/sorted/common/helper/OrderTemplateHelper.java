package com.sorted.common.helper;

import com.sorted.common.beans.TableConfig;
import com.sorted.common.beans.Secure_Return_Item;
import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Order_Details;
import com.sorted.common.entity.mongo.Order_Item;
import com.sorted.common.entity.mongo.Secure_Return;
import com.sorted.common.entity.service.Order_Item_Service;
import com.sorted.common.enums.ColumnType;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import com.sorted.common.utils.TemplateProcessorUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderTemplateHelper {

    private final Order_Item_Service order_Item_Service;

    public String getOrderTemplateTable(Order_Details details) {
        String orderCode = details.getCode();
        String orderDate = details.getCreation_date_str();

        StringBuilder productDetails = new StringBuilder();
        AggregationFilter.SEFilter filterOI = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
        filterOI.addClause(AggregationFilter.WhereClause.eq(Order_Item.Fields.order_id, details.getId()));
        filterOI.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Order_Item> listOI = order_Item_Service.repoFind(filterOI);
        if (CollectionUtils.isEmpty(listOI)) {
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }

        for (Order_Item orderItem : listOI) {
            productDetails.append(orderItem.getProduct_name()).append("|").append(orderItem.getQuantity()).append("|");
        }

        String productDetailsString = productDetails.toString();

        TableConfig orderConfig = TemplateProcessorUtil.createTableConfig(2)
                .withTableCssClass("order-table")
                .withNoDataMessage("No order data available")
                .addInfoSection("Order Number", 0, "order-info")
                .addInfoSection("Order Date", 1, "order-info")
                .addColumn("Sr. No.", ColumnType.SERIAL_NUMBER)
                .addColumn("Product Name", ColumnType.DATA)
                .addColumn("Quantity", ColumnType.DATA)
                .build();

        // Create config map
        Map<String, TableConfig> tableConfigs = new HashMap<>();
        tableConfigs.put("orderDetails", orderConfig);

        // Usage
        String template = "<div>{{orderDetails}}</div>";
        String content = orderCode + "|" + orderDate + "|" + productDetailsString;
        return TemplateProcessorUtil.replacePlaceholders(template, content, tableConfigs);
    }

    public String getSecureReturnTemplateTable(Secure_Return secureReturn) {
        String secureOrderCode = secureReturn.getSecure_order_code();
        String scheduledDate = secureReturn.getScheduled_pickup_date() != null 
                ? secureReturn.getScheduled_pickup_date().toString() 
                : "";
        String timeSlot = secureReturn.getScheduled_time_slot() != null 
                ? secureReturn.getScheduled_time_slot().getDisplayName() + " between " + secureReturn.getScheduled_time_slot().getTimeRange()
                : "";

        StringBuilder itemDetails = new StringBuilder();
        List<Secure_Return_Item> items = secureReturn.getItems();
        if (CollectionUtils.isEmpty(items)) {
            throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
        }

        for (Secure_Return_Item item : items) {
            itemDetails.append(item.getProduct_name()).append("|")
                    .append(item.getQuantity()).append("|")
                    .append(formatAmount(item.getEstimated_refund_amount())).append("|");
        }

        String itemDetailsString = itemDetails.toString();

        TableConfig secureReturnConfig = TemplateProcessorUtil.createTableConfig(3)
                .withTableCssClass("order-table")
                .withNoDataMessage("No secure return data available")
                .addInfoSection("SecuRe Order Code", 0, "secure-info")
                .addInfoSection("Scheduled Pickup Date", 1, "secure-info")
                .addInfoSection("Time Slot", 2, "secure-info")
                .addColumn("Sr. No.", ColumnType.SERIAL_NUMBER)
                .addColumn("Product Name", ColumnType.DATA)
                .addColumn("Quantity", ColumnType.DATA)
                .addColumn("Estimated Refund", ColumnType.DATA)
                .build();

        Map<String, TableConfig> tableConfigs = new HashMap<>();
        tableConfigs.put("secureReturnDetails", secureReturnConfig);

        String template = "<div>{{secureReturnDetails}}</div>";
        String content = secureOrderCode + "|" + scheduledDate + "|" + timeSlot + "|" + itemDetailsString;
        return TemplateProcessorUtil.replacePlaceholders(template, content, tableConfigs);
    }

    private String formatAmount(Long amount) {
        if (amount == null) {
            return "₹0";
        }
        return "₹" + amount;
    }
}

