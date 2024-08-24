package com.sorted.portal.bl_services;

import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sorted.commons.entity.mongo.Seller;
import com.sorted.commons.enums.ResponseCode;
import com.sorted.commons.exceptions.CustomIllegalArgumentsException;
import com.sorted.commons.helper.AggregationFilter.SEFilter;
import com.sorted.commons.helper.AggregationFilter.SEFilterType;
import com.sorted.commons.helper.AggregationFilter.WhereClause;
import com.sorted.commons.helper.SERequest;
import com.sorted.commons.helper.SEResponse;
import com.sorted.commons.utils.CommonUtils;
import com.sorted.commons.utils.SERegExpUtils;
import com.sorted.portal.request.beans.CheckDeliveryBean;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class ManageDelivery_BLService {

//	@Autowired
//	private Seller_Service seller_Service;

	@PostMapping("/checkDelivery")
	public SEResponse checkDelivery(@RequestBody SERequest request, HttpServletRequest servletRequest) {

		try {
			log.info("/checkDelivery:: API started!");
			CheckDeliveryBean req = request.getGenericRequestDataObject(CheckDeliveryBean.class);
			CommonUtils.extractHeaders(servletRequest, req);

			if (!StringUtils.hasText(req.getPincode())) {
				throw new CustomIllegalArgumentsException(ResponseCode.MANDATE_PINCODE);
			}
			if (!SERegExpUtils.isPincode(req.getPincode())) {
				throw new CustomIllegalArgumentsException(ResponseCode.INVALID_PINCODE);
			}

			SEFilter filter = new SEFilter(SEFilterType.AND);
			filter.addClause(WhereClause.eq(Seller.Fields.serviceable_pincodes, req.getPincode()));

		} catch (CustomIllegalArgumentsException ex) {
			throw ex;
		} catch (Exception e) {
			log.error("checkDelivery:: exception occurred");
			log.error("checkDelivery:: {}", e.getMessage());
			throw new CustomIllegalArgumentsException(ResponseCode.ERR_0001);
		}
		return null;
	}
}
