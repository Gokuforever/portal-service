package com.sorted.portal.service.order;

import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Order_Details;
import com.sorted.commons.entity.mongo.Order_Item;
import com.sorted.commons.entity.mongo.Seller;
import com.sorted.commons.entity.service.Order_Details_Service;
import com.sorted.commons.entity.service.Order_Item_Service;
import com.sorted.commons.entity.service.Seller_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.Permission;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.portal.request.beans.FindOrderReqBean;
import com.sorted.portal.response.beans.FindOrderResBean;
import com.sorted.portal.response.beans.OrderItemReportsDTO;
import com.sorted.portal.response.beans.OrderReportDTO;
import com.sorted.portal.service.ExcelGenerationUtility;
import com.sorted.portal.service.FileGeneratorUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for searching and retrieving orders
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderSearchService {

    private final Order_Details_Service orderDetailsService;
    private final Order_Item_Service orderItemService;
    private final Users_Service usersService;
    private final OrderFilterBuilder filterBuilder;
    private final OrderResponseMapper responseMapper;
    private final Seller_Service sellerService;

    /**
     * Search for orders for internal users
     *
     * @param req                The request object
     * @param httpServletRequest HTTP servlet request
     * @return SEResponse containing order results
     */
    public SEResponse findOrdersInternal(FindOrderReqBean req, HttpServletRequest httpServletRequest) {
        log.info("Searching orders for internal user: {}", req.getReq_user_id());

        try {
            // Extract headers
            CommonUtils.extractHeaders(httpServletRequest, req);

            // Validate user permissions
            UsersBean usersBean = usersService.validateUserForActivity(
                    req.getReq_user_id(), Permission.VIEW, Activity.ORDER_MANAGEMENT);

            // Build filter
            SEFilter orderFilter = filterBuilder.buildOrderFilter(req, usersBean);

            // Fetch orders
            List<Order_Details> ordersList = orderDetailsService.repoFind(orderFilter);
            if (CollectionUtils.isEmpty(ordersList)) {
                log.info("No orders found matching criteria");
                return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
            }

            log.debug("Found {} orders matching criteria", ordersList.size());

            // Fetch related data
            Map<String, List<Order_Item>> mapOI = fetchRelatedData(ordersList);

            // Map to response beans
            List<FindOrderResBean> resList = ordersList.stream()
                    .map(order -> responseMapper.mapToInternalResponse(order, mapOI))
                    .toList();

            log.info("Returning {} orders to internal user", resList.size());
            return SEResponse.getBasicSuccessResponseList(resList, ResponseCode.SUCCESSFUL);

        } catch (CustomIllegalArgumentsException ex) {
            log.error("Validation error searching orders: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            log.error("Unexpected error searching orders", e);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    /**
     * Search for orders for customers
     *
     * @param req                The request object
     * @param httpServletRequest HTTP servlet request
     * @return SEResponse containing order results
     */
    public SEResponse findOrdersForCustomer(FindOrderReqBean req, HttpServletRequest httpServletRequest) {
        log.info("Searching orders for customer: {}", req.getReq_user_id());

        try {
            // Extract headers
            CommonUtils.extractHeaders(httpServletRequest, req);

            // Validate user permissions
            UsersBean usersBean = usersService.validateUserForActivity(
                    req.getReq_user_id(), Permission.VIEW, Activity.ORDER_MANAGEMENT);

            // Build filter
            SEFilter orderFilter = filterBuilder.buildOrderFilter(req, usersBean);

            // Fetch orders
            List<Order_Details> ordersList = orderDetailsService.repoFind(orderFilter);
            if (CollectionUtils.isEmpty(ordersList)) {
                log.info("No orders found for customer");
                return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
            }

            log.debug("Found {} orders for customer", ordersList.size());

            // Fetch related data
            Map<String, List<Order_Item>> mapOI = fetchRelatedData(ordersList);

            List<String> sellerIds = ordersList.stream().map(Order_Details::getSeller_id).toList();
            AggregationFilter.SEFilter filter = new AggregationFilter.SEFilter(AggregationFilter.SEFilterType.AND);
            filter.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            filter.addClause(AggregationFilter.WhereClause.eq(BaseMongoEntity.Fields.id, sellerIds));
            List<Seller> sellers = sellerService.repoFind(filter);

            Map<String, Seller> mapS = sellers.stream().collect(Collectors.toMap(Seller::getId, seller -> seller));

            // Map to response beans
            List<FindOrderResBean> resList = ordersList.stream()
                    .map(order -> responseMapper.mapToCustomerResponse(order, mapOI, mapS))
                    .toList();

            log.info("Returning {} orders to customer", resList.size());
            return SEResponse.getBasicSuccessResponseList(resList, ResponseCode.SUCCESSFUL);

        } catch (CustomIllegalArgumentsException ex) {
            log.error("Validation error searching customer orders: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            log.error("Unexpected error searching customer orders", e);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    /**
     * Generate order report as Excel
     *
     * @param req                The request object
     * @param httpServletRequest HTTP servlet request
     * @return SEResponse containing Excel file bytes
     */
    public SEResponse generateOrderReport(FindOrderReqBean req, HttpServletRequest httpServletRequest) {
        log.info("Generating order report for user: {}", req.getReq_user_id());

        try {
            // Extract headers
            CommonUtils.extractHeaders(httpServletRequest, req);

            // Validate user permissions
            UsersBean usersBean = usersService.validateUserForActivity(
                    req.getReq_user_id(), Permission.VIEW, Activity.ORDER_MANAGEMENT);

            // Build filter
            SEFilter orderFilter = filterBuilder.buildOrderFilter(req, usersBean);

            // Fetch orders
            List<Order_Details> ordersList = orderDetailsService.repoFind(orderFilter);
            if (CollectionUtils.isEmpty(ordersList)) {
                log.info("No orders found for report");
                return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
            }

            log.debug("Found {} orders for report", ordersList.size());

            // Fetch order items
            List<String> orderIds = ordersList.stream()
                    .map(BaseMongoEntity::getId)
                    .toList();

            SEFilter orderItemsFilter = filterBuilder.buildOrderItemsFilter(orderIds);
            List<Order_Item> orderItems = orderItemService.repoFind(orderItemsFilter);

            log.debug("Found {} order items for report", orderItems.size());

            // Create report DTOs
            @SuppressWarnings("unchecked")
            List<OrderReportDTO> orders = (List<OrderReportDTO>)
                    responseMapper.createReportDTOs(ordersList, orderItems).get("orders");

            @SuppressWarnings("unchecked")
            List<OrderItemReportsDTO> orderItemDTOs = (List<OrderItemReportsDTO>)
                    responseMapper.createReportDTOs(ordersList, orderItems).get("orderItems");

            // Generate Excel
            Map<String, FileGeneratorUtil.SheetConfig<?, ?>> sheetConfig =
                    responseMapper.createReportSheetConfig(orders, orderItemDTOs);

            byte[] excelBytes = ExcelGenerationUtility.createExcelFileInMemory(sheetConfig);

            log.info("Successfully generated order report with {} bytes", excelBytes.length);
            return SEResponse.getBasicSuccessResponseObject(excelBytes, ResponseCode.SUCCESSFUL);

        } catch (CustomIllegalArgumentsException ex) {
            log.error("Validation error generating report: {}", ex.getMessage());
            throw ex;
        } catch (Exception e) {
            log.error("Unexpected error generating report", e);
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }

    /**
     * Fetch related data for orders
     *
     * @param ordersList List of orders
     * @return Map containing related data
     */
    private Map<String, List<Order_Item>> fetchRelatedData(List<Order_Details> ordersList) {
        // Get order IDs
        List<String> orderIds = ordersList.stream()
                .map(BaseMongoEntity::getId)
                .toList();

        // Fetch order items
        SEFilter orderItemsFilter = filterBuilder.buildOrderItemsFilter(orderIds);
        List<Order_Item> orderItems = orderItemService.repoFind(orderItemsFilter);

        return responseMapper.groupOrderItemsByOrderId(orderItems);
    }
} 