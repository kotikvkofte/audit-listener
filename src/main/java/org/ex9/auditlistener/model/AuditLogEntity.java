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
import java.util.UUID;

/**
 * JPA-сущность для хранения Audit-логов в базе данных.
 * @author Краковцев Артём
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "audit-methods")
public class AuditLogEntity {

    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private UUID auditId;

    @Field(type = FieldType.Keyword)
    private String messageId;

    @Field(type = FieldType.Text)
    private String eventType;

    @Field(type = FieldType.Text)
    private String method;

    @Field(type = FieldType.Text)
    private String args;

    @Field(type = FieldType.Text)
    private String result;

    @Field(type = FieldType.Text)
    private String error;

    @Field(type = FieldType.Keyword)
    private String level;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant timestamp;

}
