package com.sorted.common.beans;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Attempt_Details {

	private String otp;
	private LocalDateTime attempt_date;
	private String ip_address;
}
