package com.sorted.portal.bl_services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.beans.Item;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Cart;
import com.sorted.commons.entity.mongo.Order_Item;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.service.Cart_Service;
import com.sorted.commons.entity.service.ProductService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.portal.request.beans.PayNowBean;

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

	@PostMapping("/pay")
	public SEResponse pay(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
		try {
			log.info("auth/verifyOtp:: API started!");
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
			Set<String> product_ids = cart_items.stream().map(e -> e.getProduct_id()).collect(Collectors.toSet());
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
				order_Item.setQuantity(item.getQuantity());
				order_Item.setProduct_id(product.getId());
				order_Item.setProduct_code(product.getProduct_code());
				order_Item.setSelling_price(product.getSelling_price());
				order_Item.setTotal_cost(product.getSelling_price() * item.getQuantity());
				listOI.add(order_Item);

			}
			
//			Order_Details order_Details = new Order_Details();
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("signin:: exception occurred");
			log.error("signin:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
		return null;
	}
}
