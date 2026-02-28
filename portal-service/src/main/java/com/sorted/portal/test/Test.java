package com.sorted.portal.test;

import com.sorted.common.entity.mongo.Users;
import com.sorted.common.entity.service.Users_Service;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.helper.SEResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;



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
