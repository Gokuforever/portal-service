package com.sorted.portal.test;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.entity.mongo.Users;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.helper.SEResponse;



@RestController
public class Test {
	
	@Autowired
	private Users_Service users_Service;

	@PostMapping("/test")
	public SEResponse test() {
		List<Users> listUsers = users_Service.repoFindAll();
		return SEResponse.getBasicSuccessResponseList(listUsers, ResponseCode.SUCCESSFUL);
	}
}
