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
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Cart;
import com.sorted.commons.entity.mongo.Category_Master;
import com.sorted.commons.entity.mongo.Products;
import com.sorted.commons.entity.service.Cart_Service;
import com.sorted.commons.entity.service.Category_MasterService;
import com.sorted.commons.entity.service.ProductService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.Permission;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.portal.assisting.beans.CartItems;
import com.sorted.portal.assisting.beans.CartItemsBean;
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

	@Autowired
	private Users_Service users_Service;

	@Autowired
	private Category_MasterService category_MasterService;

	@PostMapping("/fetch")
	public SEResponse fetch(HttpServletRequest httpServletRequest) {
		try {
			String req_user_id = httpServletRequest.getHeader("req_user_id");
			UsersBean usersBean = users_Service.validateUserForActivity(req_user_id, Permission.VIEW,
					Activity.CART_MANAGEMENT);
			switch (usersBean.getRole().getUser_type()) {
			case CUSTOMER, GUEST:
				break;
			default:
				throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
			}
			SEFilter filterC = new SEFilter(SEFilterType.AND);
			filterC.addClause(WhereClause.eq(Cart.Fields.user_id, req_user_id));
			filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			Cart cart = cart_Service.repoFindOne(filterC);
			if (cart == null) {
				cart = new Cart();
				cart.setUser_id(req_user_id);
				cart = cart_Service.create(cart, req_user_id);
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
			CartCRUDBean req = request.getGenericRequestDataObject(CartCRUDBean.class);
			CommonUtils.extractHeaders(httpServletRequest, req);
			UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.MANAGE_ADDRESS);
			if (req.getItem() == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.NO_ITEMS);
			}

			CartItemsBean itemBean = req.getItem();
			if (!StringUtils.hasText(itemBean.getProduct_id())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRODUCT_ID);
			}
			if (itemBean.getQuantity() == null || itemBean.getQuantity() < 0) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_PRODUCT_QUANTITY);
			}

			SEFilter filterC = new SEFilter(SEFilterType.AND);
			filterC.addClause(WhereClause.eq(Cart.Fields.user_id, usersBean.getId()));
			filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

			Cart cart = cart_Service.repoFindOne(filterC);
			if (cart == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
			}
			List<Item> listItems = new ArrayList<>();
			if (itemBean.getQuantity() == 0) {
				List<Item> cart_items = cart.getCart_items().stream()
						.filter(e -> !e.getProduct_id().equals(itemBean.getProduct_id())).toList();
				if (!CollectionUtils.isEmpty(cart_items)) {
					listItems.addAll(cart_items);
				}
			} else {
				SEFilter filterP = new SEFilter(SEFilterType.AND);
				filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, itemBean.getProduct_id()));
				filterP.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

				Products product = productService.repoFindOne(filterP);
				if (product == null) {
					throw new CustomIllegalArgumentsException(ResponseCode.ITEM_NOT_FOUND);
				}

				if (product.getQuantity().compareTo(itemBean.getQuantity()) < 0) {
					throw new CustomIllegalArgumentsException(ResponseCode.OUT_OF_STOCK);
				}

				SEFilter filterCM = new SEFilter(SEFilterType.AND);
				filterCM.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, product.getCategory_id()));
				filterCM.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

				Category_Master category_Master = category_MasterService.repoFindOne(filterCM);
				if (category_Master == null) {
					throw new CustomIllegalArgumentsException(ResponseCode.MISSING_CATAGORY);
				}

				boolean secure_item = category_Master.isSecure_item();
				boolean is_secure_item = itemBean.isSecure_item();
				if (is_secure_item && !secure_item) {
					throw new CustomIllegalArgumentsException(ResponseCode.CANNOT_SECURE);
				}

				Item item = new Item();
				item.setProduct_id(product.getId());
				item.setQuantity(itemBean.getQuantity());
				item.setProduct_code(product.getProduct_code());
				item.set_secure(is_secure_item);

				listItems.add(item);
				if (!CollectionUtils.isEmpty(cart.getCart_items())) {
					listItems.addAll(cart.getCart_items());
				}
				cart.getCart_items().add(item);
			}

			cart.setCart_items(listItems);

			cart_Service.update(cart.getId(), cart, usersBean.getId());

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

			List<Products> listP = productService.repoFind(filterC);
			if (!CollectionUtils.isEmpty(listP)) {
				Map<String, Products> mapP = listP.stream().collect(Collectors.toMap(p -> p.getId(), p -> p));
				cart_items.stream().forEach(e -> {
					if (mapP.containsKey(e.getProduct_id())) {
						CartItems items = new CartItems();
						Products products = mapP.get(e.getProduct_id());
						items.setProduct_name(products.getName());
						items.setProduct_code(e.getProduct_code());
						items.setQuantity(e.getQuantity());
						items.setSelling_price(products.getSelling_price());
						items.setIn_stock(products.getQuantity().compareTo(e.getQuantity()) >= 0);
						items.setSecure_item(e.is_secure());
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
