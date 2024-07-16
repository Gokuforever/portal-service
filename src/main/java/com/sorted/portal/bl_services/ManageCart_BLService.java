package com.sorted.portal.bl_services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.beans.Item;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Cart;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.service.Cart_Service;
import com.sorted.commons.entity.service.ProductService;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.portal.assisting.beans.CartItems;
import com.sorted.portal.request.beans.CartCRUDBean;
import com.sorted.portal.response.beans.CartBean;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class ManageCart_BLService {

	@Autowired
	private Cart_Service cart_Service;

	@Autowired
	private ProductService productService;

	@PostMapping("/create")
	public SEResponse create(HttpServletRequest httpServletRequest) {
		try {
			String req_user_id = httpServletRequest.getHeader("req_user_id");
			String req_role_id = httpServletRequest.getHeader("req_role_id");
			SEFilter filterC = new SEFilter(SEFilterType.AND);
			filterC.addClause(WhereClause.eq(Cart.Fields.user_id, req_user_id));
			filterC.addClause(WhereClause.eq(Cart.Fields.role_id, req_role_id));
			filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			Cart cart = cart_Service.repoFindOne(filterC);
			if (cart == null) {
				cart = new Cart();
			}

			CartBean cartBean = this.getCartBean(filterC, cart);
			return SEResponse.getBasicSuccessResponseObject(cartBean, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("/signup/verify:: exception occurred");
			log.error("/signup/verify:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	@PostMapping("/fetch")
	public SEResponse fetch(HttpServletRequest httpServletRequest) {
		try {
			String req_user_id = httpServletRequest.getHeader("req_user_id");
			String req_role_id = httpServletRequest.getHeader("req_role_id");
			if (!StringUtils.hasText(req_user_id)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_USER_ID);
			}
			if (!StringUtils.hasText(req_role_id)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ROLE_ID);
			}
			SEFilter filterC = new SEFilter(SEFilterType.AND);
			filterC.addClause(WhereClause.eq(Cart.Fields.user_id, req_user_id));
			filterC.addClause(WhereClause.eq(Cart.Fields.role_id, req_role_id));
			filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			Cart cart = cart_Service.repoFindOne(filterC);
			if (cart == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
			}

			CartBean cartBean = this.getCartBean(filterC, cart);
			return SEResponse.getBasicSuccessResponseObject(cartBean, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("/signup/verify:: exception occurred");
			log.error("/signup/verify:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}
	
	@PostMapping("/addToCart")
	public SEResponse update(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
		try {
			String req_user_id = httpServletRequest.getHeader("req_user_id");
			String req_role_id = httpServletRequest.getHeader("req_role_id");
			if (!StringUtils.hasText(req_user_id)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_USER_ID);
			}
			if (!StringUtils.hasText(req_role_id)) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ROLE_ID);
			}
			CartCRUDBean req = request.getGenericRequestDataObject(CartCRUDBean.class);
			if(CollectionUtils.isEmpty(req.getItems())) {
//				throw new CustomIllegalArgumentsException(ResponseCode.);
			}
			SEFilter filterC = new SEFilter(SEFilterType.AND);
			filterC.addClause(WhereClause.eq(Cart.Fields.user_id, req_user_id));
			filterC.addClause(WhereClause.eq(Cart.Fields.role_id, req_role_id));
			filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			Cart cart = cart_Service.repoFindOne(filterC);
			if (cart == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
			}

			CartBean cartBean = this.getCartBean(filterC, cart);
			return SEResponse.getBasicSuccessResponseObject(cartBean, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("/signup/verify:: exception occurred");
			log.error("/signup/verify:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	private CartBean getCartBean(SEFilter filterC, Cart cart) {
		CartBean cartBean = new CartBean();
		List<CartItems> cartItems = new ArrayList<>();
		List<Item> cart_items = cart.getCart_items();
		if (!CollectionUtils.isEmpty(cart_items)) {
			List<String> product_ids = cart_items.stream().map(e -> e.getProduct_id()).collect(Collectors.toList());
			SEFilter filterP = new SEFilter(SEFilterType.AND);
			filterP.addClause(WhereClause.in(BaseMongoEntity.Fields.id, product_ids));
			filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			List<Products> products = productService.repoFind(filterC);
			if (!CollectionUtils.isEmpty(products)) {
				Map<String, String> mapP = products.stream()
						.collect(Collectors.toMap(p -> p.getId(), p -> p.getName()));
				cart_items.stream().forEach(e -> {
					if (mapP.containsKey(e.getProduct_id())) {
						CartItems items = new CartItems();
						items.setProduct_name(mapP.get(e.getProduct_id()));
						items.setProduct_code(e.getProduct_code());
						items.setQuantity(e.getQuantity());
						items.setSelling_price(e.getPrice());
						cartItems.add(items);
					}
				});
			}
			cartBean.setTotal_amount(cart.getTotal_price());
		}
		cartBean.setCart_items(cartItems);
		return cartBean;
	}

}
