package com.sorted.portal.bl_services;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.beans.Spoc_Details;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Order_Details;
import com.sorted.commons.entity.mongo.Order_Item;
import com.sorted.commons.entity.mongo.Role;
import com.sorted.commons.entity.mongo.Seller;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Order_Details_Service;
import com.sorted.commons.entity.service.Order_Item_Service;
import com.sorted.commons.entity.service.Seller_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.OrderStatus;
import com.sorted.commons.enums.Permission;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.porter.req.beans.CreateOrderBean;
import com.sorted.commons.porter.req.beans.CreateOrderBean.Address;
import com.sorted.commons.porter.req.beans.CreateOrderBean.Contact_Details;
import com.sorted.commons.porter.req.beans.CreateOrderBean.Delivery_Instructions;
import com.sorted.commons.porter.req.beans.CreateOrderBean.Drop_Details;
import com.sorted.commons.porter.req.beans.CreateOrderBean.Instruction_List;
import com.sorted.commons.porter.req.beans.CreateOrderBean.Pickup_Details;
import com.sorted.commons.porter.res.beans.CreateOrderResBean;
import com.sorted.commons.porter.res.beans.FetchOrderRes.FareDetails;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.portal.request.beans.CreateDeliveryBean;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/change/status")
public class ManageOrder_BLService {

	@Autowired
	private Order_Details_Service order_Details_Service;

	@Autowired
	private Order_Item_Service order_Item_Service;

	@Autowired
	private Seller_Service seller_Service;

	@Autowired
	private Users_Service users_Service;

	@Autowired
	private PorterUtility porterUtility;

	@PostMapping("/readyForPickup")
	public SEResponse createOrder(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
		try {
			log.info("create/order:: API started!");

			CreateDeliveryBean req = request.getGenericRequestDataObject(CreateDeliveryBean.class);
			CommonUtils.extractHeaders(httpServletRequest, req);
			UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Permission.EDIT,
					Activity.INVENTORY_MANAGEMENT);

			Role role = usersBean.getRole();
			UserType user_type = role.getUser_type();
			if (user_type != UserType.SELLER) {
				throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
			}
			if (!StringUtils.hasText(req.getOrder_id())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_ORDER_ID);
			}

			SEFilter filterOD = new SEFilter(SEFilterType.AND);
			filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getOrder_id()));
			filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			Order_Details order_Details = order_Details_Service.repoFindOne(filterOD);
			if (order_Details == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
			}

			if (order_Details.getStatus() != OrderStatus.TRANSACTION_PROCESSED) {
				throw new CustomIllegalArgumentsException(ResponseCode.INVALID_ORDER_STATUS);
			}

			SEFilter filterOI = new SEFilter(SEFilterType.AND);
			filterOI.addClause(WhereClause.eq(Order_Item.Fields.order_id, req.getOrder_id()));
			filterOI.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			List<Order_Item> listOI = order_Item_Service.repoFind(filterOI);
			if (CollectionUtils.isEmpty(listOI)) {
				throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
			}

			List<String> seller_ids = listOI.stream().map(e -> e.getSeller_id()).distinct().toList();
			if (seller_ids.size() > 1) {
				// TODO: need to discuss
			}
			String seller_id = seller_ids.get(0);

			SEFilter filterS = new SEFilter(SEFilterType.AND);
			filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, seller_id));
			filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, seller_id));

			Seller seller = seller_Service.repoFindOne(filterS);
			if (seller == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
			}

			Optional<Spoc_Details> primary_spoc = seller.getSpoc_details().stream().filter(e -> e.isPrimary())
					.findFirst();
			if (primary_spoc.isEmpty()) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRIMARY_SPOC);
			}
			Spoc_Details spoc_Details = primary_spoc.get();
			AddressDTO delivery_address = order_Details.getDelivery_address();
			AddressDTO pickup_address = order_Details.getPickup_address();
			String user_id = order_Details.getUser_id();

			SEFilter filterU = new SEFilter(SEFilterType.AND);
			filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, user_id));
			filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			Users users = users_Service.repoFindOne(filterU);
			if (users == null) {
				// TODO:
			}

			CreateOrderBean order = getCreateOrderReq(order_Details, spoc_Details, delivery_address, pickup_address,
					users);

			CreateOrderResBean createOrderResBean = porterUtility.createOrder(order);
			String order_id = createOrderResBean.getOrder_id();
			order_Details.setStatus(OrderStatus.READY_FOR_PICK_UP, usersBean.getId());
			order_Details.setDp_order_id(order_id);
			order_Details.setFare_details(FareDetails.builder()
					.estimated_fare_details(createOrderResBean.getEstimated_fare_details()).build());
			order_Details.setEstimated_pickup_time(createOrderResBean.getEstimated_pickup_time());

			listOI.stream().forEach(e -> {
				e.setStatus(OrderStatus.READY_FOR_PICK_UP, usersBean.getId());
				order_Item_Service.update(e.getId(), e, usersBean.getId());
			});

			order_Details_Service.update(order_Details.getId(), order_Details, usersBean.getId());

		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("checkDelivery:: exception occurred");
			log.error("checkDelivery:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
		return null;
	}

	private CreateOrderBean getCreateOrderReq(Order_Details order_Details, Spoc_Details spoc_Details,
			AddressDTO delivery_address, AddressDTO pickup_address, Users users) {
		// @formatter:off
		// Create delivery instructions
		Delivery_Instructions instruction1 = Delivery_Instructions.builder()
		        .type("text")
		        .description("handle with care")
		        .build();

		Instruction_List instructionsList = Instruction_List.builder()
		        .instructions_list(Arrays.asList(instruction1))
		        .build();

		// Create pickup details
		Address pickupAddress = Address.builder()
		        .street_address1(pickup_address.getStreet_1())
		        .street_address2(pickup_address.getStreet_2())
		        .landmark(pickup_address.getLandmark())
		        .city(pickup_address.getCity())
		        .state(pickup_address.getState())
		        .pincode(pickup_address.getPincode())
		        .country("India")
		        .lat(pickup_address.getLat())
		        .lng(pickup_address.getLng())
		        .contact_details(Contact_Details.builder()
		                .name(spoc_Details.getFirst_name() + " " + spoc_Details.getLast_name())
		                .phone_number("+91"+spoc_Details.getMobile_no())
		                .build())
		        .build();

		Pickup_Details pickupDetails = Pickup_Details.builder()
		        .address(pickupAddress)
		        .build();

		// Create drop details
		Address dropAddress = Address.builder()
		        .street_address1(delivery_address.getStreet_1())
		        .street_address2(delivery_address.getStreet_2())
		        .landmark(delivery_address.getLandmark())
		        .city(delivery_address.getCity())
		        .state(delivery_address.getState())
		        .pincode(delivery_address.getPincode())
		        .country("India")
		        .lat(delivery_address.getLat())
		        .lng(delivery_address.getLng())
		        .contact_details(Contact_Details.builder()
		                .name(users.getFirst_name() + " " + users.getLast_name())
		                .phone_number("+91" + users.getMobile_no())
		                .build())
		        .build();

		Drop_Details dropDetails = Drop_Details.builder()
		        .address(dropAddress)
		        .build();

		// Create the main order bean
		return CreateOrderBean.builder()
		        .request_id(order_Details.getCode())
		        .delivery_instructions(instructionsList)
		        .pickup_details(pickupDetails)
		        .drop_details(dropDetails)
		        .build();
	}
}
