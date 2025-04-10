package com.sorted.portal.crons;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Order_Details;
import com.sorted.commons.entity.mongo.Order_Item;
import com.sorted.commons.entity.service.Order_Details_Service;
import com.sorted.commons.entity.service.Order_Item_Service;
import com.sorted.commons.enums.OrderStatus;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.porter.res.beans.FetchOrderRes;
import com.sorted.commons.utils.PorterUtility;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ManageCrons_BLService {

	@Autowired
	private Order_Details_Service order_Details_Service;

	@Autowired
	private PorterUtility porterUtility;

	@Autowired
	private Order_Item_Service order_Item_Service;

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
			break;
		case live:
			currentOrderStatus = OrderStatus.OUT_FOR_DELIVERY;
			break;
		default:
			break;
		}
		if (currentOrderStatus != null && details.getStatus() != currentOrderStatus) {
			SEFilter filterOI = new SEFilter(SEFilterType.AND);
			filterOI.addClause(WhereClause.eq(Order_Item.Fields.order_id, details.getId()));
			filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			List<Order_Item> listOI = order_Item_Service.repoFind(filterOI);
			if (CollectionUtils.isEmpty(listOI)) {
				throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
			}
			final OrderStatus finalOrderStatus = currentOrderStatus;

			listOI.stream().forEach(e -> {
				e.setStatus(finalOrderStatus, Defaults.PORTER_STCHK_CRON);
				order_Item_Service.update(e.getId(), e, Defaults.PORTER_STCHK_CRON);
			});
			details.setFare_details(fetchOrderRes.getFare_details());
			details.setStatus(finalOrderStatus, Defaults.PORTER_STCHK_CRON);
			order_Details_Service.update(details.getId(), details, Defaults.PORTER_STCHK_CRON);
		}
	}
}
