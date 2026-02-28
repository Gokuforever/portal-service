package com.sorted.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;

@Getter
@AllArgsConstructor
public enum Semester {

	SEM_1("Sem 1"), SEM_2("Sem 2"), SEM_3("Sem 3"), SEM_4("Sem 4"), SEM_5("Sem 5"), SEM_6("Sem 6"), SEM_7("Sem 7"),
	SEM_8("Sem 8");

	private final String alias;

	private static final Map<String, Semester> by_alias = new HashMap<>();

	static {
		for (Semester sem : values()) {
			by_alias.put(sem.getAlias(), sem);
		}
	}

	public static Semester getByAlias(@NonNull String s) {
		try {
			return by_alias.get(s);
		} catch (Exception e) {
			return null;
		}
	}
}
