package com.sorted.common.beans;

import com.sorted.common.entity.mongo.Role;
import com.sorted.common.entity.mongo.Seller;
import com.sorted.common.entity.mongo.Users;
import lombok.*;

import java.io.Serial;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class UsersBean extends Users {
	@Serial
	private static final long serialVersionUID = 1L;
	private Role role;
	private String token;
	private Seller seller;
	private String refresh_token;

}
