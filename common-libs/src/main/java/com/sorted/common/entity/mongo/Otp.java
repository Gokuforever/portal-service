package com.sorted.common.entity.mongo;

import com.sorted.common.beans.Attempt_Details;
import com.sorted.common.enums.ProcessType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = false)
@FieldNameConstants
@Document(collection = "otp")
public class Otp extends BaseMongoEntity<String> {

	/**
	 * 
	 */
	@Serial
	private static final long serialVersionUID = 1L;

	private String otp_value;
	private Boolean is_verified;
	private Boolean status;
	private Integer max_attempt;
	private Integer verification_attempt;
	private Integer resend_attempt;
	private String mobile_no;
	private String email_id;
	private String uuid = UUID.randomUUID().toString();
	private LocalDateTime expiry_at;
	private LocalDateTime verified_at;
	private List<Attempt_Details> attempt_details;
	private ProcessType process_type;
}
