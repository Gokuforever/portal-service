package com.sorted.common.helper;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.utils.GsonUtils;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.springframework.http.HttpStatus;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
public class SEResponse implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private HttpStatus status;
	String userMessage;
	String responseDataType;
	String responseData;
	String responseCode;
	String errorMessage;
	String sysErrorMessage;

	@JsonIgnore
	public static SEResponse getEmptySuccessResponse(ResponseCode message) {
		SEResponse apiResponse;
		apiResponse = SEResponse.builder().status(HttpStatus.OK).responseCode(message.getCode())
				.userMessage(message.getUserMessage()).responseData("").responseDataType("").build();

		return apiResponse;
	}

	@JsonIgnore
	public static SEResponse getEmptySuccessResponse(String message) {
		SEResponse apiResponse;
		apiResponse = SEResponse.builder().status(HttpStatus.OK).userMessage(message).responseData("")
				.responseDataType("").build();

		return apiResponse;
	}

	@JsonIgnore
	public static SEResponse getBasicSuccessResponseObject(@NonNull Object e, ResponseCode message) {
		if (e instanceof Iterable) {
			String err = "Don't Use list in this constructor";

			return getGenericFailureResponse(err);
		}

		return SEResponse.builder().status(HttpStatus.OK).userMessage(message.getUserMessage())
				.responseCode(message.getCode()).errorMessage(message.getErrorMessage())
				.responseData(GsonUtils.getGson().toJson(e)).responseDataType(e.getClass().getSimpleName()).build();
	}

	@JsonIgnore
	public static SEResponse getBasicSuccessResponseList(@NonNull List<?> list, ResponseCode message) {

		return SEResponse.builder().status(HttpStatus.OK).userMessage(message.getUserMessage())
				.responseCode(message.getCode()).errorMessage(message.getErrorMessage())
				.responseData(GsonUtils.getGson().toJson(list)).responseDataType(list.getClass().getSimpleName())
				.build();
	}

	@JsonIgnore
	public static SEResponse getBadRequestFailureResponse(ResponseCode error) {
		String inErrorMessage = "Bad request";
		return SEResponse.builder().status(HttpStatus.BAD_REQUEST).errorMessage(inErrorMessage)
				.userMessage(error.getUserMessage()).sysErrorMessage(error.getErrorMessage())
				.responseCode(error.getCode()).build();
	}

	@JsonIgnore
	public static SEResponse getGenericFailureResponse(String inSysErrorMessage) {
		String inErrorMessage = "Something went wrong";
		SEResponse apiResponse = SEResponse.builder().status(HttpStatus.SERVICE_UNAVAILABLE)
				.errorMessage(inErrorMessage).sysErrorMessage(inSysErrorMessage).build();
		return apiResponse;
	}

}
