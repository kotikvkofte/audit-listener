package org.ex9.auditlistener.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/**
 * DTO для передачи данных об HTTP событии.
 * @author Краковцев Артём
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpLogDto implements LogDto {

    @JsonProperty(required = true)
    private String messageId;

    /** Время запроса. */
    @Builder.Default
    @JsonProperty(required = true)
    private String timestamp = LocalDateTime.now().toString();

    /** Направление запроса (Incoming/Outgoing). */
    @JsonProperty(required = true)
    private String direction;

    /** HTTP-метод (GET, POST и т.д.). */
    @JsonProperty(required = true)
    private String method;

    /** Код статуса HTTP-запроса (200, 400 и т.д.). */
    @JsonProperty(required = true)
    private int statusCode;

    /** URL запроса, включая параметры. */
    @JsonProperty(required = true)
    private String url;

    /** Тело запроса. */
    @Field(type = FieldType.Text)
    private String requestBody;

    /** Тело ответа. */
    @Field(type = FieldType.Text)
    private String responseBody;


}
