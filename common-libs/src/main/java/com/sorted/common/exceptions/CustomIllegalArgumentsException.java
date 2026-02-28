package com.sorted.common.exceptions;

import com.sorted.common.enums.ResponseCode;
import lombok.NonNull;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class CustomIllegalArgumentsException extends BaseException {

	/**
	 * 
	 */
	@Serial
	private static final long serialVersionUID = 1L;

	public CustomIllegalArgumentsException(@NonNull ResponseCode err) {
		super(err);
	}

	public CustomIllegalArgumentsException(@NonNull ResponseCode err, @NonNull HttpStatus status) {
		super(err, status);
	}

	public CustomIllegalArgumentsException(@NonNull String err) {
		super(err);
	}

}
