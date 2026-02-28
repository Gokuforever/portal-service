package com.sorted.common.helper;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ReqBaseBean {

	protected String req_user_id;
	protected int page;
	protected int size;
}
