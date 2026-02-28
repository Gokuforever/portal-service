package com.sorted.common.test;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

public class Test {

	@Data
	@ToString(callSuper = true)
	class A {
		private String a1;
	}

	@Data
	@ToString(callSuper = true)
	@EqualsAndHashCode(callSuper = false)
	class B extends A {
		private String b;
	}

	public static String toTitleCase(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}

		// Trim leading/trailing spaces and replace multiple spaces with a single space
		input = input.trim().replaceAll("\\s+", " ");

		String[] words = input.split(" "); // Split by single space now
		StringBuilder titleCased = new StringBuilder();

		for (String word : words) {
			if (word.length() > 0) {
				String firstLetter = word.substring(0, 1).toUpperCase(); // Capitalize first letter
				String remaining = word.substring(1).toLowerCase(); // Lowercase the rest
				titleCased.append(firstLetter).append(remaining).append(" ");
			}
		}

		// Remove the last extra space
		return titleCased.toString().trim();
	}

	public static void main(String[] args) {
		String input = "   yogesh sunil ajsdk    khaire! 123abc";
		String output = toTitleCase(input);
		System.out.println(output); // Outputs: "Yogesh Sunil Khaire! 123abc"
	}
}
