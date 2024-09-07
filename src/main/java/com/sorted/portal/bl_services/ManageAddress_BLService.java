package com.sorted.portal.bl_services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.Address;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.service.Address_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.Activity;
import com.sorted.commons.enums.Permission;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.enums.UserType;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.ValidationUtil;
import com.sorted.portal.request.beans.AddressBean;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestMapping("/address")
@RestController
public class ManageAddress_BLService {

	@Autowired
	private Users_Service users_Service;

	@Autowired
	private Address_Service address_Service;

	@PostMapping("/add")
	public SEResponse add(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
		try {
			log.info("address/add:: API started!");
			AddressBean req = request.getGenericRequestDataObject(AddressBean.class);
			CommonUtils.extractHeaders(httpServletRequest, req);
			UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Permission.EDIT,
					Activity.MANAGE_ADDRESS);
			UserType user_type = usersBean.getRole().getUser_type();
			switch (user_type) {
			case CUSTOMER:
			case GUEST:
				break;
			default:
				throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
			}

			AddressDTO addressDTO = req.getAddress();
			if (addressDTO == null) {
				throw new CustomIllegalArgumentsException(ResponseCode.MISSING_ADDRESS);
			}
			SEFilter filterA = new SEFilter(SEFilterType.AND);
			filterA.addClause(WhereClause.eq(Address.Fields.entity_id, usersBean.getId()));
			filterA.addClause(WhereClause.eq(Address.Fields.user_type, user_type.name()));
			
			List<Address> listA = address_Service.repoFind(filterA);
			if(!CollectionUtils.isEmpty(listA)) {
				
			}
			
			Address address = ValidationUtil.validateAddress(addressDTO, new Address());
			address.setUser_type(user_type);
			address.setEntity_id(usersBean.getId());
			address_Service.create(address, req.getReq_user_id());

			return SEResponse.getEmptySuccessResponse(ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("add/address:: exception occurred");
			log.error("add/address:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}

	@PostMapping("/fetch")
	public SEResponse fetch(@RequestBody SERequest request, HttpServletRequest httpServletRequest) {
		try {
			log.info("add/fetch:: API started!");
			AddressBean req = request.getGenericRequestDataObject(AddressBean.class);
			CommonUtils.extractHeaders(httpServletRequest, req);
			UsersBean usersBean = users_Service.validateUserForActivity(req.getReq_user_id(), Activity.MANAGE_ADDRESS);
			switch (usersBean.getRole().getUser_type()) {
			case CUSTOMER:
			case GUEST:
				break;
//			case SELLER:
//			case SUPER_ADMIN:
//				if (!StringUtils.hasText(req.getCus_user_id())) {
//					throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_CUST_USER_ID);
//				}
//				SEFilter filterU = new SEFilter(SEFilterType.AND);
//				filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getCus_user_id()));
//				filterU.addClause(WhereClause.eq(BaseMongoEntity.Fields.id, req.getCus_user_id()));
//				
//				Users users = users_Service.repoFindOne(filterU);
//				if(users==null) {
//					// Throw exception
//				}
//				
//				break;
			default:
				throw new CustomIllegalArgumentsException(ResponseCode.ACCESS_DENIED);
			}
			List<AddressDTO> listA = new ArrayList<>();
			SEFilter filterA = new SEFilter(SEFilterType.AND);
			filterA.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
			filterA.addClause(WhereClause.eq(Address.Fields.entity_id, usersBean.getId()));

			List<Address> addresses = address_Service.repoFind(filterA);
			if (!CollectionUtils.isEmpty(addresses)) {
				addresses.stream().forEach(e -> {
					AddressDTO dto = new AddressDTO();
					dto.setStreet_1(e.getStreet_1());
					dto.setStreet_2(e.getStreet_2());
					dto.setLandmark(e.getLandmark());
					dto.setCity(e.getCity());
					dto.setState(e.getState());
					dto.setPincode(e.getPincode());
					dto.setAddress_type(e.getAddress_type().getType());
					dto.setAddress_type_desc(e.getAddress_type_desc());
					dto.setCode(e.getCode());
					dto.setIs_default(e.getIs_default());
					listA.add(dto);
				});
			}
			return SEResponse.getBasicSuccessResponseList(listA, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("add/fetch:: exception occurred");
			log.error("add/fetch:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}
}
