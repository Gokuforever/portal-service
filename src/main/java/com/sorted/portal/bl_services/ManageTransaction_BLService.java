package com.sorted.portal.bl_services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.razorpay.Order;
import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.beans.Item;
import com.sorted.commons.beans.Order_Status_History;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.Address;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Cart;
import com.sorted.commons.entity.mongo.Order_Details;
import com.sorted.commons.entity.mongo.Order_Item;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.service.Address_Service;
import com.sorted.commons.entity.service.Cart_Service;
import com.sorted.commons.entity.service.Order_Details_Service;
import com.sorted.commons.entity.service.Order_Item_Service;
import com.sorted.commons.entity.service.ProductService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.OrderStatus;
import com.sorted.commons.enums.PurchaseType;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.portal.razorpay.CheckoutReqbean;
import com.sorted.portal.razorpay.RazorpayUtility;
import com.sorted.portal.request.beans.PayNowBean;
import com.sorted.portal.response.beans.PGResponseBean;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class ManageTransaction_BLService {

	@Autowired
	private Users_Service users_Service;

	@Autowired
	private Cart_Service cart_Service;

	@Autowired
	private ProductService productService;

	@Autowired
	private Address_Service address_Service;

	@Autowired
	private Order_Details_Service order_Details_Service;

	@Autowired
	private Order_Item_Service order_Item_Service;

	@Autowired
	private RazorpayUtility razorpayUtility;

//	@PostMapping("/findAll")
//	public SEResponse find(@RequestBody SERequest request) {
//		OrderDetailsReqBean req = request.getGenericRequestDataObject(OrderDetailsReqBean.class);
//		SEFilter filterSE = new SEFilter(SEFilterType.AND);
//		if (StringUtils.hasText(req.getOrder_code())) {
//			filterSE.addClause(WhereClause.eq(Order_Details.Fields.code, req.getOrder_code()));
//		}
//		if (StringUtils.hasText(req.getUser())) {
//			SEFilterNode node = new SEFilterNode(SEFilterType.OR);
////			node.addClause(WhereClause.eq(Order_Details.Fields.user_code, req.getUser()));
//			node.addClause(WhereClause.eq(Order_Details.Fields.user_id, req.getUser()));
//			filterSE.addNodes(node);
//		}
//		if (StringUtils.hasText(req.getOrder_status())) {
//			int statusId = OrderStatus.getByStatus(req.getOrder_status());
//			if (statusId == 0) {
//				throw new IllegalArgumentException("Invalid status");
//			}
//			filterSE.addClause(WhereClause.eq(Order_Details.Fields.status_id, statusId));
//		}
//		if (StringUtils.hasText(req.getTransaction_id())) {
//			filterSE.addClause(WhereClause.eq(Order_Details.Fields.transaction_id, req.getTransaction_id()));
//		}
////		if (StringUtils.hasText(req.getFrom_date()) && StringUtils.hasText(req.getTo_date())) {
////			LocalDateTime from = LocalDate.parse(req.getFrom_date()).atTime(LocalTime.MIN);
////			LocalDateTime to = LocalDate.parse(req.getFrom_date()).atTime(LocalTime.MAX);
////			filterSE.addClause(WhereClause.gteq(BaseMongoEntity.Fields.creation_date, from));
////			filterSE.addClause(WhereClause.lteq(BaseMongoEntity.Fields.creation_date, to));
////		}
//		List<Order_Details> ordersList = order_Details_Service.repoFind(filterSE);
//		if (CollectionUtils.isEmpty(ordersList)) {
//			return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
//		}
//		return SEResponse.getBasicSuccessResponseList(ordersList, ResponseCode.SUCCESSFUL);
//	}

	@PostMapping("/pay")
	public SEResponse pay(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
		try {
			log.info("/pay:: API started!");
			PayNowBean req = request.getGenericRequestDataObject(PayNowBean.class);
			CommonUtils.extractHeaders(httpServletRequest, req);
			UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.PURCHASE);
			switch (usersBean.getRole().getUser_type()) {
			case CUSTOMER:
				break;
			case GUEST:
				throw new CustomIllegalArgumentsException(ResponseCode.PROMT_SIGNUP);
			default:
				throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
			}
			if (!StringUtils.hasText(req.getDelivery_address_id())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_DELIVERY_ADD);
			}

			SEFilter filterA = new SEFilter(SEFilterType.AND);
			filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getDelivery_address_id()));
			filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
			filterA.addClause(WhereClause.eq(Address.Fields.entity_id, usersBean.getId()));
			filterA.addClause(WhereClause.eq(Address.Fields.user_type, UserType.CUSTOMER.name()));

			Address address = address_Service.repoFindOne(filterA);
			if (address == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.ADDRESS_NOT_FOUND);
			}

			SEFilter filterC = new SEFilter(SEFilterType.AND);
			filterC.addClause(WhereClause.eq(Cart.Fields.user_id, usersBean.getId()));
			filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			Cart cart = cart_Service.repoFindOne(filterC);
			if (cart == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
			}

			List<Item> cart_items = cart.getCart_items();
			if (CollectionUtils.isEmpty(cart_items)) {
				throw new CustomIllegalArgumentsException(ResponseCode.CART_EMPTY);
			}
			LocalDateTime return_date = null;
			boolean has_secure = cart_items.stream().anyMatch(Item::is_secure);
			if (has_secure) {
				if (!StringUtils.hasText(req.getReturn_date())) {
					throw new CustomIllegalArgumentsException(ResponseCode.MISSING_RETURN_DATE);
				}
				try {
					return_date = LocalDate.parse(req.getReturn_date()).atTime(LocalTime.MAX);
				} catch (Exception e) {
					log.error("pay:: Exception occurred:: {}", e.getMessage());
					throw new CustomIllegalArgumentsException(ResponseCode.INVALID_RETURN_DATE);
				}
			}
			Set<String> product_ids = cart_items.stream().map(Item::getProduct_id).collect(Collectors.toSet());

			SEFilter filterP = new SEFilter(SEFilterType.AND);
			filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, CommonUtils.convertS2L(product_ids)));
			filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			List<Products> listP = productService.repoFind(filterP);
			if (CollectionUtils.isEmpty(listP)) {
				throw new CustomIllegalArgumentsException(ResponseCode.OUT_OF_STOCK);
			}
			Map<String, Products> mapP = listP.stream().collect(Collectors.toMap(e -> e.getId(), e -> e));

			List<Order_Item> listOI = new ArrayList<>();

			for (Item item : cart_items) {
				if (!mapP.containsKey(item.getProduct_id())) {
					throw new CustomIllegalArgumentsException(ResponseCode.DELETED_PRODUCT);
				}
				Products product = mapP.get(item.getProduct_id());
				if (product.getQuantity().compareTo(item.getQuantity()) < 0) {
					throw new CustomIllegalArgumentsException(ResponseCode.FEW_OUT_OF_STOCK);
				}

				Order_Item order_Item = new Order_Item();
				order_Item.setProduct_id(product.getId());
				order_Item.setProduct_code(product.getProduct_code());
				order_Item.setQuantity(item.getQuantity());
				order_Item.setSelling_price(product.getSelling_price());
				order_Item.setSeller_id(product.getSeller_id());
				order_Item.setSeller_code(product.getSeller_code());
				order_Item.setTotal_cost(product.getSelling_price() * item.getQuantity());
				if (item.is_secure()) {
					order_Item.setType(PurchaseType.SECURE);
					order_Item.setReturn_date(return_date);
				} else {
					order_Item.setType(PurchaseType.BUY);
				}
				listOI.add(order_Item);
			}
			long totalSum = listOI.stream().mapToLong(Order_Item::getTotal_cost).sum();
			if (totalSum < 1) {
				throw new CustomIllegalArgumentsException(ResponseCode.INVALID_AMOUNT);
			}

			Order_Details order = new Order_Details();

			if (StringUtils.hasText(usersBean.getId())) {
				order.setUser_id(usersBean.getId());
			}
//			FIXME: need to add product total amount, delivery charges, gst and other charges in total amount 
//			order.setDelivery_charges();
			order.setTotal_amount(totalSum);
			order.setStatus(OrderStatus.ORDER_REQUESTED);
			order.setStatus_id(OrderStatus.ORDER_REQUESTED.getId());
			Order_Status_History order_history = new Order_Status_History();
			order_history.setStatus(OrderStatus.ORDER_REQUESTED);
			order_history.setModification_date(LocalDateTime.now());
			order_history.setModified_by(usersBean.getId());
			order.setOrder_status_history(Arrays.asList(order_history));

			//@formatter:off
			AddressDTO del_address =  new AddressDTO();
			if (StringUtils.hasText(address.getStreet_1())){del_address.setStreet_1(address.getStreet_1());}
			if (StringUtils.hasText(address.getStreet_2())){del_address.setStreet_2(address.getStreet_2());}
			if (StringUtils.hasText(address.getLandmark())){del_address.setLandmark(address.getLandmark());}
			if (StringUtils.hasText(address.getCity())){del_address.setCity(address.getCity());}
			if (StringUtils.hasText(address.getState())){del_address.setState(address.getState());}
			if (StringUtils.hasText(address.getPincode())){del_address.setPincode(address.getPincode());}
			if (address.getAddress_type()!=null){del_address.setAddress_type(address.getAddress_type().name());}
			if (StringUtils.hasText(address.getAddress_type_desc())){del_address.setAddress_type_desc(address.getAddress_type_desc());}
			order.setDelivery_address(del_address);
			// @formatter:on
			Order_Details order_Details = order_Details_Service.create(order, usersBean.getId());

			for (Order_Item order_Item : listOI) {
				order_Item.setOrder_id(order_Details.getId());
				order_Item.setOrder_code(order_Details.getCode());
				order_Item_Service.create(order_Item, usersBean.getId());
			}
			Order rzrp_order = razorpayUtility.createOrder(totalSum, order_Details.getId());
			if (rzrp_order == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.PG_ORDER_GEN_FAILED);
			}
			CheckoutReqbean checkoutPayload = razorpayUtility.createCheckoutPayload(rzrp_order);
			return SEResponse.getBasicSuccessResponseObject(checkoutPayload, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("/pay:: exception occurred");
			log.error("/pay:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	@PostMapping("/saveResponse")
	public SEResponse saveResponse(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
		try {
			log.info("/saveResponse:: API started!");
			PGResponseBean req = request.getGenericRequestDataObject(PGResponseBean.class);
			if (!StringUtils.hasText(req.getOrder_id())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_ORDER_ID);
			}
			if (!StringUtils.hasText(req.getRazorpay_order_id()) || !StringUtils.hasText(req.getRazorpay_payment_id())
					|| !StringUtils.hasText(req.getRazorpay_signature())) {
				throw new CustomIllegalArgumentsException(ResponseCode.PG_BAD_REQ);
			}
			SEFilter filterO = new SEFilter(SEFilterType.AND);
			filterO.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getOrder_id()));
			filterO.addClause(WhereClause.eq(Order_Details.Fields.status, OrderStatus.ORDER_REQUESTED.name()));
			filterO.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
			Order_Details order_details = order_Details_Service.repoFindOne(filterO);
			if (order_details == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
			}

			order_details.setPg_order_id(req.getRazorpay_order_id());
			order_details.setTransaction_id(req.getRazorpay_payment_id());

			// TODO: verify razorpay signature to validate payment details

			order_details.setStatus(OrderStatus.PAYMENT_PROCESSED);
			order_details.setStatus_id(OrderStatus.PAYMENT_PROCESSED.getId());
			Order_Status_History order_history = new Order_Status_History();
			order_history.setStatus(OrderStatus.PAYMENT_PROCESSED);
			order_history.setModification_date(LocalDateTime.now());
			order_history.setModified_by(Defaults.SYSTEM_ADMIN);
			List<Order_Status_History> order_status_history = order_details.getOrder_status_history();
			order_status_history.add(order_history);
			order_details.setOrder_status_history(order_status_history);
			order_Details_Service.update(order_details.getId(), order_details, Defaults.SYSTEM_ADMIN);

			log.info("/saveResponse:: API ended!");
			return null;
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("/saveResponse:: exception occurred");
			log.error("/saveResponse:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}
}
