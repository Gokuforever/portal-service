package com.sorted.portal.request.beans;

import com.sorted.commons.beans.AddressDTO;
import com.sorted.commons.helper.ReqBaseBean;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class AddressBean extends ReqBaseBean {

	private String user_id;
	private AddressDTO address;
}
