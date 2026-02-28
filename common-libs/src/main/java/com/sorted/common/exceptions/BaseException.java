package com.sorted.common.exceptions;

import com.sorted.common.enums.ResponseCode;
import lombok.Getter;
import lombok.NonNull;
import org.springframework.http.HttpStatus;

import java.io.Serial;

@Getter
public abstract class BaseException extends RuntimeException {

	@Serial
	private static final long serialVersionUID = -3093653763733424129L;
	private ResponseCode responseCode;
	private String messageCode;
	private Object[] messageArgs;
	private String userMessage;
    private HttpStatus httpStatus;

	// Constructors
	// ------------------------------------------------------------------------

	/**
	 * Constructs with the message code with its place-holder values.
	 * 
	 * @param messageCode Message Code
	 * @param args        Message Arguments
	 */
	BaseException(String messageCode, Object[] args) {
		this(messageCode, args, null);
	}

    /**
	 * Constructs with the message code with its place-holder values and its cause.
	 * 
	 * @param messageCode Message Code
	 * @param args        Message Arguments
	 * @param cause       the cause.
	 */
	BaseException(String messageCode, Object[] args, Throwable cause) {
		super(cause);
		this.messageCode = messageCode;
		this.messageArgs = args;
	}

	BaseException(String message) {
		this(message, (String) null);
	}

	/**
	 * Constructs with specific messages for Application and end-user.
	 * 
	 * @param message     application exception message
	 * @param userMessage end-user exception message
	 */
	BaseException(String message, String userMessage) {
		this(message, userMessage, null);
	}

	/**
	 * Constructs with specific messages for Application and end-user.
	 * 
	 * @param message     application exception message
	 * @param userMessage end-user exception message
	 * @param cause       the cause.
	 */
	BaseException(String message, String userMessage, Throwable cause) {
		super(message, cause);

		this.userMessage = userMessage;
	}

	protected BaseException(@NonNull ResponseCode err) {
		this(err.getErrorMessage(), err.getUserMessage());
		this.responseCode = err;
		this.messageCode = err.getCode();
	}

	protected BaseException(@NonNull ResponseCode err, @NonNull HttpStatus status) {
		this(err.getErrorMessage(), err.getUserMessage());
		this.responseCode = err;
		this.messageCode = err.getCode();
		this.httpStatus = status;
	}

	protected BaseException(@NonNull ResponseCode err, String argsResponseCode, String argsUser) {
		this(err.getErrorMessage() + argsResponseCode, err.getUserMessage() + argsUser);
		this.responseCode = err;
		this.messageCode = err.getCode();
	}

	protected BaseException(@NonNull String messageCode, String message, String userMessage, Throwable cause) {
		// this(messageCode + ": " + message, userMessage, cause);
		this(message, userMessage, cause);
		this.messageCode = messageCode;
	}

	// Methods
	// ------------------------------------------------------------------------


	public Object[] getMessageArgs() {
		return messageArgs == null ? new Object[] {} : messageArgs;
	}

}
