package com.sorted.portal.bl_services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.constants.Defaults;
import com.sorted.commons.entity.mongo.Cart;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Cart_Service;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.SEResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequestMapping("/guest")
@RestController
public class ManageGuest_BLService {

	@Autowired
	private Users_Service users_Service;
	
	@Autowired
	private Cart_Service cart_Service;

	@Value("${se.guest.first_name}")
	private String guest_first_name;

	@Value("${se.guest.last_name}")
	private String guest_last_name;

	@Value("${se.guest.role_id}")
	private String guest_role_id;

	@PostMapping("/create")
	public SEResponse createGuest() {
		try {
			Users user = new Users();
			user.setFirst_name(guest_first_name);
			user.setLast_name(guest_last_name);
			user.setRole_id(guest_role_id);
			user.setIs_verified(true);

			Users guest = users_Service.create(user, Defaults.AUTO);
			
			Cart cart = new Cart();
			cart.setUser_id(guest.getId());
			cart_Service.create(cart, guest.getId());

			UsersBean usersBean = users_Service.validateAndGetUserInfo(guest.getId());
			return SEResponse.getBasicSuccessResponseObject(usersBean, ResponseCode.SUCCESSFUL);
		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("/signup/verify:: exception occurred");
			log.error("/signup/verify:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
	}
}
