package com.sorted.portal.service.order;

import com.sorted.commons.beans.TableConfig;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Order_Details;
import com.sorted.commons.entity.mongo.Order_Item;
import com.sorted.commons.entity.service.Order_Item_Service;
import com.sorted.commons.enums.ColumnType;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter;
import com.sorted.commons.utils.TemplateProcessorUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderTemplateService {

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
}
