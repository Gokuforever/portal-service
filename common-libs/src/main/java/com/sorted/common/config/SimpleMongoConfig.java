package com.sorted.common.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.NonNull;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.bson.types.Decimal128;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

@Configuration
public class SimpleMongoConfig {

    @Value("${spring.data.mongodb.uri:mongodb+srv://yogeshk:Sorted%402024@sorted.myru7yg.mongodb.net/}")
    private String mongoUri;

    @Value("${spring.data.mongodb.database:sorted}")
    private String mongoDatabase;

    @Bean
    public MongoClient mongo() {
        final ConnectionString connectionString = new ConnectionString(mongoUri);
        CodecRegistry pojoCodecRegistry = fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                fromProviders(PojoCodecProvider.builder().automatic(true).build()));
        final MongoClientSettings mongoClientSettings = MongoClientSettings.builder().codecRegistry(pojoCodecRegistry)
                .applyConnectionString(connectionString).build();
        return MongoClients.create(mongoClientSettings);
    }

    @Bean
    public MongoTemplate mongoTemplate() {
        MongoTemplate mongoTemplate = new MongoTemplate(mongo(), mongoDatabase);
        mongoTemplate.setWriteConcern(WriteConcern.ACKNOWLEDGED);
        MappingMongoConverter conv = (MappingMongoConverter) mongoTemplate.getConverter();
        conv.setCustomConversions(mongoCustomConversions());
        conv.afterPropertiesSet();

        System.out.println("MongoTemplate connected to database: " + mongoTemplate.getDb().getName());

        return mongoTemplate;
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(
                Arrays.asList(new BigDecimalDecimal128Converter(), new Decimal128BigDecimalConverter()));

    }

    @WritingConverter
    private static class BigDecimalDecimal128Converter implements Converter<BigDecimal, Decimal128> {
        @Override
        public Decimal128 convert(@NonNull BigDecimal source) {
            return new Decimal128(source);
        }
    }

    @ReadingConverter
    private static class Decimal128BigDecimalConverter implements Converter<Decimal128, BigDecimal> {

        @Override
        public BigDecimal convert(@NonNull Decimal128 source) {
            return source.bigDecimalValue();
        }
    }

    // ---------- LocalDateTime (IST) <-> Date ----------
    @WritingConverter
    private static class LocalDateTimeWriteConverter implements Converter<LocalDateTime, Date> {
        @Override
        public Date convert(@NonNull LocalDateTime source) {
            // Convert IST LocalDateTime → Instant (UTC) → Date
            ZonedDateTime zdt = source.atZone(ZoneId.of("Asia/Kolkata"));
            return Date.from(zdt.toInstant());
        }
    }

    @ReadingConverter
    private static class LocalDateTimeReadConverter implements Converter<Date, LocalDateTime> {
        @Override
        public LocalDateTime convert(@NonNull Date source) {
            // Convert UTC Date → Instant → IST LocalDateTime
            Instant instant = source.toInstant();
            return instant.atZone(ZoneId.of("Asia/Kolkata")).toLocalDateTime();
        }
    }

}
