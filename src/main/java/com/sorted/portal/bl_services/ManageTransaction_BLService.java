package com.sorted.portal.bl_services;

import com.razorpay.Order;
import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.beans.Item;
import com.sorted.commons.beans.Order_Status_History;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.*;
import com.sorted.commons.entity.service.*;
import com.sorted.commons.enums.*;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.porter.req.beans.GetQuoteRequest;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.PorterUtility;
import com.sorted.portal.razorpay.CheckoutReqbean;
import com.sorted.portal.razorpay.RazorpayUtility;
import com.sorted.portal.request.beans.FindOrderReqBean;
import com.sorted.portal.request.beans.PayNowBean;
import com.sorted.portal.response.beans.FndOrderResBean;
import com.sorted.portal.response.beans.PGResponseBean;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
public class ManageTransaction_BLService {

    private final Users_Service users_Service;
    private final Cart_Service cart_Service;
    private final ProductService productService;
    private final Address_Service address_Service;
    private final Order_Details_Service order_Details_Service;
    private final Order_Item_Service order_Item_Service;
    private final RazorpayUtility razorpayUtility;
    private final Seller_Service seller_Service;
    private final PorterUtility porterUtility;

    @Autowired
    public ManageTransaction_BLService(Users_Service users_Service, Cart_Service cart_Service, ProductService productService,
                                       Address_Service address_Service, Order_Details_Service order_Details_Service, Order_Item_Service order_Item_Service,
                                       RazorpayUtility razorpayUtility, Seller_Service seller_Service, PorterUtility porterUtility) {
        this.users_Service = users_Service;
        this.cart_Service = cart_Service;
        this.productService = productService;
        this.address_Service = address_Service;
        this.order_Details_Service = order_Details_Service;
        this.order_Item_Service = order_Item_Service;
        this.razorpayUtility = razorpayUtility;
        this.seller_Service = seller_Service;
        this.porterUtility = porterUtility;
    }

    @PostMapping("/order/find")
    public SEResponse find(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
        FindOrderReqBean req = request.getGenericRequestDataObject(FindOrderReqBean.class);
        SEFilter filterSE = new SEFilter(SEFilterType.AND);
        if (StringUtils.hasText(req.getCode())) {
            filterSE.addClause(WhereClause.eq(Order_Details.Fields.code, req.getCode()));
        }
        CommonUtils.extractHeaders(httpServletRequest, req);
        UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(),
                Activity.INVENTORY_MANAGEMENT);
        switch (usersBean.getRole().getUser_type()) {
            case SELLER, SUPER_ADMIN:
                break;
            default:
                throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
        }

        if (StringUtils.hasText(req.getOrder_status())) {
            OrderStatus orderStatus = OrderStatus.getByInternalStatus(req.getOrder_status());
            if (orderStatus == null) {
                throw new IllegalArgumentException("Invalid status");
            }
            filterSE.addClause(WhereClause.eq(Order_Details.Fields.status_id, orderStatus.getId()));
        }

        if (StringUtils.hasText(req.getFrom_date()) && StringUtils.hasText(req.getTo_date())) {
            LocalDateTime from = LocalDate.parse(req.getFrom_date()).atTime(LocalTime.MIN);
            LocalDateTime to = LocalDate.parse(req.getFrom_date()).atTime(LocalTime.MAX);
            filterSE.addClause(WhereClause.gte(BaseMongoEntity.Fields.creation_date, from));
            filterSE.addClause(WhereClause.lte(BaseMongoEntity.Fields.creation_date, to));
        }

        filterSE.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Order_Details> ordersList = order_Details_Service.repoFind(filterSE);
        if (CollectionUtils.isEmpty(ordersList)) {
            return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
        }

        List<FndOrderResBean> resList = ordersList.stream().map(this::entToBean).toList();
        return SEResponse.getBasicSuccessResponseList(resList, ResponseCode.SUCCESSFUL);
    }

    private FndOrderResBean entToBean(Order_Details order_Details) {
        return FndOrderResBean.builder().id(order_Details.getId()).code(order_Details.getCode())
                .status(order_Details.getStatus().getInternal_status())
                .transaction_id(order_Details.getTransaction_id()).total_amount(order_Details.getTotal_amount())
                .pickup_address(order_Details.getPickup_address()).delivery_address(order_Details.getDelivery_address())
                .build();
    }

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
            List<String> seller_ids = listP.stream().map(Products::getSeller_id).distinct().toList();
            if (seller_ids.isEmpty()) {
                // TODO: need to discuss
            }
            String seller_id = seller_ids.get(0);
            SEFilter filterS = new SEFilter(SEFilterType.AND);
            filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, seller_id));
            filterS.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

            Seller seller = seller_Service.repoFindOne(filterS);
            if (seller == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
            }

            SEFilter filterSA = new SEFilter(SEFilterType.AND);
            filterSA.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            filterSA.addClause(WhereClause.eq(Address.Fields.entity_id, seller.getId()));
            filterSA.addClause(WhereClause.eq(Address.Fields.user_type, UserType.SELLER.name()));

            Address sellerAddress = address_Service.repoFindOne(filterSA);
            if (sellerAddress == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.ADDRESS_NOT_FOUND);
            }

            Map<String, Products> mapP = listP.stream().collect(Collectors.toMap(BaseMongoEntity::getId, e -> e));

            List<Order_Item> listOI = new ArrayList<>();

            for (Item item : cart_items) {
                if (!mapP.containsKey(item.getProduct_id())) {
                    throw new CustomIllegalArgumentsException(ResponseCode.DELETED_PRODUCT);
                }
                Products product = mapP.get(item.getProduct_id());
                if (product == null) {
                    continue;
                }
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
                order_Item.setStatus(OrderStatus.ORDER_PLACED);
                order_Item.setStatus_id(OrderStatus.ORDER_PLACED.getId());
                order_Item.setTotal_cost(product.getSelling_price() * item.getQuantity());
                if (item.is_secure()) {
                    order_Item.setType(PurchaseType.SECURE);
                    order_Item.setReturn_date(return_date);
                } else {
                    order_Item.setType(PurchaseType.BUY);
                }
                listOI.add(order_Item);
            }
            Map<String, Long> mapPQ = listOI.stream()
                    .collect(Collectors.toMap(Order_Item::getProduct_id, Order_Item::getQuantity));
            long totalSum = listOI.stream().mapToLong(Order_Item::getTotal_cost).sum();
            if (totalSum < 1) {
                throw new CustomIllegalArgumentsException(ResponseCode.INVALID_AMOUNT);
            }

            // @formatter:off
						GetQuoteRequest quoteRequest = GetQuoteRequest.builder()
				                .pickup_details(GetQuoteRequest.PickupDetails.builder()
				                        .lat(sellerAddress.getLat().doubleValue())
				                        .lng(sellerAddress.getLng().doubleValue())
				                        .build())
				                .drop_details(GetQuoteRequest.DropDetails.builder()
				                        .lat(address.getLat().doubleValue())
				                        .lng(address.getLng().doubleValue())
				                        .build())
				                .customer(GetQuoteRequest.Customer.builder()
				                        .name(usersBean.getFirst_name() + " " + usersBean.getLast_name())
				                        .mobile(GetQuoteRequest.Customer.Mobile.builder()
				                                .country_code("+91")
				                                .number(usersBean.getMobile_no())
				                                .build())
				                        .build())
				                .build();
						// @formatter:on
            porterUtility.getQuote(quoteRequest, usersBean.getId());

            Order_Details order = new Order_Details();

            if (StringUtils.hasText(usersBean.getId())) {
                order.setUser_id(usersBean.getId());
            }
//			FIXME: need to add product total amount, delivery charges, gst and other charges in total amount 
//			order.setDelivery_charges();
            order.setTotal_amount(totalSum);
            order.setStatus(OrderStatus.ORDER_PLACED);
            order.setStatus_id(OrderStatus.ORDER_PLACED.getId());
            Order_Status_History order_history = Order_Status_History.builder().status(OrderStatus.ORDER_PLACED)
                    .modification_date(LocalDateTime.now()).modified_by(usersBean.getId()).build();
            order.setOrder_status_history(Collections.singletonList(order_history));

            //@formatter:off
			AddressDTO del_address =  new AddressDTO();
			if (StringUtils.hasText(address.getStreet_1())){del_address.setStreet_1(address.getStreet_1());}
			if (StringUtils.hasText(address.getStreet_2())){del_address.setStreet_2(address.getStreet_2());}
			if (StringUtils.hasText(address.getLandmark())){del_address.setLandmark(address.getLandmark());}
			if (StringUtils.hasText(address.getCity())){del_address.setCity(address.getCity());}
			if (StringUtils.hasText(address.getState())){del_address.setState(address.getState());}
			if (StringUtils.hasText(address.getPincode())){del_address.setPincode(address.getPincode());}
			if (address.getAddress_type()!=null){del_address.setAddress_type(address.getAddress_type().name());}
			del_address.setLat(address.getLat());
			del_address.setLng(address.getLng());
			if (StringUtils.hasText(address.getAddress_type_desc())){del_address.setAddress_type_desc(address.getAddress_type_desc());}
			
			AddressDTO pickup_address =  new AddressDTO();
			if (StringUtils.hasText(sellerAddress.getStreet_1())){pickup_address.setStreet_1(sellerAddress.getStreet_1());}
			if (StringUtils.hasText(sellerAddress.getStreet_2())){pickup_address.setStreet_2(sellerAddress.getStreet_2());}
			if (StringUtils.hasText(sellerAddress.getLandmark())){pickup_address.setLandmark(sellerAddress.getLandmark());}
			if (StringUtils.hasText(sellerAddress.getCity())){pickup_address.setCity(sellerAddress.getCity());}
			if (StringUtils.hasText(sellerAddress.getState())){pickup_address.setState(sellerAddress.getState());}
			if (StringUtils.hasText(sellerAddress.getPincode())){pickup_address.setPincode(sellerAddress.getPincode());}
			if (sellerAddress.getAddress_type()!=null){pickup_address.setAddress_type(sellerAddress.getAddress_type().name());}
			if (StringUtils.hasText(sellerAddress.getAddress_type_desc())){pickup_address.setAddress_type_desc(sellerAddress.getAddress_type_desc());}
			pickup_address.setLat(sellerAddress.getLat());
			pickup_address.setLng(sellerAddress.getLng());
			order.setPickup_address(pickup_address);
			order.setDelivery_address(del_address);
			// @formatter:on

            for (Products product : mapP.values()) {
                Long quantity = mapPQ.getOrDefault(product.getId(), null);
                if (quantity == null) {
                    continue;
                }
                quantity = product.getQuantity() - quantity;
                product.setQuantity(quantity);
                productService.update(product.getId(), product, Defaults.SYSTEM_ADMIN);
            }

//			cart.setCart_items(null);
//			cart_Service.update(cart.getId(), cart, usersBean.getId());

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
            JSONObject json = rzrp_order.toJson();
            String pg_order_id = json.get("id").toString();
            order_Details.setPg_order_id(pg_order_id);
            order_Details_Service.update(order_Details.getId(), order_Details, usersBean.getId());

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
//			if (!StringUtils.hasText(req.getOrder_id())) {
//				throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_ORDER_ID);
//			}
            if (!StringUtils.hasText(req.getRazorpay_order_id()) || !StringUtils.hasText(req.getRazorpay_payment_id())
                    || !StringUtils.hasText(req.getRazorpay_signature())) {
                throw new CustomIllegalArgumentsException(ResponseCode.PG_BAD_REQ);
            }
            SEFilter filterO = new SEFilter(SEFilterType.AND);
            filterO.addClause(WhereClause.eq(Order_Details.Fields.pg_order_id, req.getRazorpay_order_id()));
//			filterO.addClause(WhereClause.eq(Order_Details.Fields.status, OrderStatus.ORDER_PLACED.name()));
            filterO.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            Order_Details order_details = order_Details_Service.repoFindOne(filterO);
            if (order_details == null) {
                throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
            }
            SEFilter filterOD = new SEFilter(SEFilterType.AND);
            filterOD.addClause(WhereClause.eq(Order_Item.Fields.order_id, order_details.getId()));
            filterOD.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
            List<Order_Item> items = order_Item_Service.repoFind(filterOD);
            if (CollectionUtils.isEmpty(items)) {
                throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
            }

            List<String> product_ids = items.stream().map(Order_Item::getProduct_id).toList();

            OrderStatus orderStatus;
            if (!StringUtils.hasText(req.getRazorpay_payment_id())) {
                orderStatus = OrderStatus.TRANSACTION_FAILED;
                // TODO: should we increase the product quantity
            } else {
                boolean verified = razorpayUtility.verifySignature(req.getRazorpay_order_id(),
                        req.getRazorpay_payment_id(), req.getRazorpay_signature());
                if (!verified) {
                    throw new CustomIllegalArgumentsException(ResponseCode.UNTRUSTED_RESPONSE);
                }

                SEFilter filterC = new SEFilter(SEFilterType.AND);
                filterC.addClause(WhereClause.eq(Cart.Fields.user_id, order_details.getUser_id()));
                filterC.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
                Cart cart = cart_Service.repoFindOne(filterC);
                if (cart == null) {
                    throw new CustomIllegalArgumentsException(ResponseCode.NO_RECORD);
                }

                List<Item> collect = cart.getCart_items().stream().filter(e -> !product_ids.contains(e.getProduct_id()))
                        .toList();
                if (!CollectionUtils.isEmpty(items)) {
                    cart.setCart_items(collect);
                } else {
                    cart.setCart_items(null);
                }
                cart_Service.update(cart.getId(), cart, Defaults.SYSTEM_ADMIN);

                order_details.setTransaction_id(req.getRazorpay_payment_id());
                orderStatus = OrderStatus.TRANSACTION_PROCESSED;
            }

            final OrderStatus finalOrderStatus = orderStatus;

            items.forEach(e -> {
                e.setStatus(finalOrderStatus, Defaults.SYSTEM_ADMIN);
                order_Item_Service.update(e.getId(), e, Defaults.SYSTEM_ADMIN);
            });
            order_details.setStatus(finalOrderStatus, Defaults.SYSTEM_ADMIN);
            order_Details_Service.update(order_details.getId(), order_details, Defaults.SYSTEM_ADMIN);

            log.info("/saveResponse:: API ended!");
            return SEResponse.getEmptySuccessResponse(ResponseCode.ORDER_PLACED);
        } catch (CustomIllegalArgumentsException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("/saveResponse:: exception occurred");
            log.error("/saveResponse:: {}", e.getMessage());
            throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
        }
    }
}
