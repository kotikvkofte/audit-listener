package org.ex9.auditlistener.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA-сущность для хранения HTTP-логов в базе данных.
 * @author Краковцев Артём
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "audit-requests")
public class HttpLogEntity {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String messageId;

    @Field(type = FieldType.Date, format = DateFormat.epoch_millis)
    private Long timestamp;

    @Field(type = FieldType.Keyword)
    private String direction;

    @Field(type = FieldType.Text)
    private String method;

    @Field(type = FieldType.Keyword)
    private Integer statusCode;

    @Field(type = FieldType.Text)
    private String url;

    @Field(type = FieldType.Text)
    private String requestBody;

    @Field(type = FieldType.Text)
    private String responseBody;

}
