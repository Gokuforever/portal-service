package com.sorted.portal.bl_services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.beans.UsersBean;
import com.sorted.commons.entity.mongo.BaseMongoEntity;
import com.sorted.commons.entity.mongo.Category_Master;
import com.sorted.commons.entity.service.Category_MasterService;
import com.sorted.commons.entity.service.Users_Service;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SEResponse;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class ManageMetaData_BLService {

	@Autowired
	private Category_MasterService category_MasterService;

	@Autowired
	private Users_Service users_Service;

	@PostMapping("/getMetaData")
	public SEResponse getMetaData() {
		log.info("getMetaData:: API started");
		SEFilter filterCM = new SEFilter(SEFilterType.AND);
		filterCM.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
		filterCM.addProjection(Category_Master.Fields.name, Category_Master.Fields.sub_categories,
				Category_Master.Fields.category_code);
		List<Category_Master> listCM = category_MasterService.repoFind(filterCM);
		if (CollectionUtils.isEmpty(listCM)) {
			return SEResponse.getEmptySuccessResponse(ResponseCode.NO_RECORD);
		}
		log.info("getMetaData:: API ended");
		return SEResponse.getBasicSuccessResponseList(listCM, ResponseCode.SUCCESSFUL);
	}

	@PostMapping("/getUserInfo")
	public SEResponse getUserInfo(HttpServletRequest httpServletRequest) {
		try {
			String req_user_id = httpServletRequest.getHeader("req_user_id");
			String req_role_id = httpServletRequest.getHeader("req_role_id");
			UsersBean usersBean = users_Service.validateAndGetUserInfo(req_user_id, req_role_id);
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
