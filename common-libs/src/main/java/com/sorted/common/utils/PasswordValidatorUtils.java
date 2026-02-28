package com.sorted.common.utils;

import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.CustomIllegalArgumentsException;

import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PasswordValidatorUtils {

	private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
	private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
	private static final String DIGITS = "0123456789";
	private static final String SPECIAL_CHARACTERS = "!@#$%&*+<>?";
	private static final int PASSWORD_LENGTH = 20;

	private static final SecureRandom random = new SecureRandom();

	public static void validatePassword(String password) {

		Pattern lowerletter = Pattern.compile("[a-z]");
		Pattern upperletter = Pattern.compile("[A-Z]");
		Pattern digit = Pattern.compile("\\d");
		Pattern special = Pattern.compile("[!@#$%&*()_+=|<>?{}\\[\\]~-]");
		Pattern eight = Pattern.compile(".{8}");

		Matcher hasLowerLetter = lowerletter.matcher(password);
		Matcher hasUpperLetter = upperletter.matcher(password);
		Matcher hasDigit = digit.matcher(password);
		Matcher hasSpecial = special.matcher(password);
		Matcher hasEight = eight.matcher(password);

		if (!hasLowerLetter.find()) {
			throw new CustomIllegalArgumentsException(ResponseCode.PASS_VALIDATION_FAILURE_1);
		}

		if (!hasUpperLetter.find()) {
			throw new CustomIllegalArgumentsException(ResponseCode.PASS_VALIDATION_FAILURE_2);
		}

		if (!hasDigit.find()) {
			throw new CustomIllegalArgumentsException(ResponseCode.PASS_VALIDATION_FAILURE_3);
		}

		if (!hasSpecial.find()) {
			throw new CustomIllegalArgumentsException(ResponseCode.PASS_VALIDATION_FAILURE_4);
		}

		if (!hasEight.find()) {
			throw new CustomIllegalArgumentsException(ResponseCode.PASS_VALIDATION_FAILURE_5);
		}
//		int nextInt = Integer.valueOf(CommonUtils.generateFixedLengthRandomNumber(1));
//		System.out.println(nextInt);
//		if (nextInt > 5) {
//			throw new CustomIllegalArgumentsException(ResponseCode.PASS_VALIDATION_FAILURE_5);
//		}
	}

	public static String generatePassword() {
		StringBuilder password = new StringBuilder();

		// Ensure at least one character from each category
		password.append(getRandomChar(LOWERCASE));
		password.append(getRandomChar(UPPERCASE));
		password.append(getRandomChar(DIGITS));
		password.append(getRandomChar(SPECIAL_CHARACTERS));

		// Fill the rest with random characters from all categories
		String allChars = LOWERCASE + UPPERCASE + DIGITS + SPECIAL_CHARACTERS;
		while (password.length() < PASSWORD_LENGTH) {
			password.append(getRandomChar(allChars));
		}

		// Shuffle the characters in the password for randomness
		String shuffleString = shuffleString(password.toString());
//		System.out.println(shuffleString);
		try {
			validatePassword(shuffleString);
			return shuffleString;
		} catch (Exception e) {
			return generatePassword();
		}
	}

	private static char getRandomChar(String characters) {
		int index = random.nextInt(characters.length());
		return characters.charAt(index);
	}

	private static String shuffleString(String input) {
		char[] chars = input.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			int randomIndex = random.nextInt(chars.length);
			char temp = chars[i];
			chars[i] = chars[randomIndex];
			chars[randomIndex] = temp;
		}
		return new String(chars);
	}

//	public static void main(String[] args) {
//		System.out.println(generatePassword());
//	}

}
