package com.sorted.common.helper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
public class SERequest implements Serializable {
	/**
	* 
	*/
	@Serial
	private static final long serialVersionUID = 1L;
	private String source;
	private String requestDataType;
	private Object requestData;
	private Object identifier;

	@JsonIgnore
	public <K> K getGenericRequestDataObject(@NonNull Class<K> clazz) {
		if (this.getRequestData() != null) {
			try {
				ObjectMapper mapper = new ObjectMapper();
				return mapper.convertValue(this.getRequestData(), clazz);
			} catch (IllegalArgumentException e) {
				System.out.println(e);
				throw new CustomIllegalArgumentsException(ResponseCode.INVALID_REQ, HttpStatus.BAD_REQUEST);
			}
		} else {
			String err = "RequestData is null";
			throw new CustomIllegalArgumentsException(err);
		}
	}
}