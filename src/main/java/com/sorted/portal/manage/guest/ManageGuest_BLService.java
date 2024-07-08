package com.sorted.portal.manage.guest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SEResponse;

@RequestMapping("/guest")
@RestController
public class ManageGuest_BLService {

	@Value("${se.portal.guest.user_id}")
	private String guest_user_id;

	@Autowired
	private Users_Service users_Service;
	
	@PostMapping("/create")
	public SEResponse createGuest() {
		try {
			Users user = new Users();
//			user.set
			
		} catch (Exception e) {
			// TODO: handle exception
		}
		return null;
	}
}
