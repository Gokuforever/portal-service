package com.sorted.common.utils;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class GsonUtils {

	private GsonUtils() {

	}

	private static final Gson gsonInstance = createGson();

	private static Gson createGson() {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.registerTypeAdapter(LocalDate.class, new LocalDateAdapter());
		gsonBuilder.registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter());
		return gsonBuilder.create();
	}

	public static Gson getGson() {
		return gsonInstance;
	}

	private static class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
		private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

		@Override
		public JsonElement serialize(LocalDate date, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive(formatter.format(date));
		}

		@Override
		public LocalDate deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			return LocalDate.parse(json.getAsString(), formatter);
		}
	}

	private static class LocalDateTimeAdapter
			implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
		private final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

		@Override
		public JsonElement serialize(LocalDateTime datetime, Type typeOfSrc, JsonSerializationContext context) {
			return new JsonPrimitive(formatter.format(datetime));
		}

		@Override
		public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			return LocalDateTime.parse(json.getAsString(), formatter);
		}
	}
}
