package org.ex9.auditlistener.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private String messageId;

    /** Время запроса. */
    @Builder.Default
    private String timestamp = LocalDateTime.now().toString();

    /** Направление запроса (Incoming/Outgoing). */
    private String direction;

    /** HTTP-метод (GET, POST и т.д.). */
    private String method;

    /** Код статуса HTTP-запроса (200, 400 и т.д.). */
    private int statusCode;

    /** URL запроса, включая параметры. */
    private String url;

    /** Тело запроса. */
    private String requestBody;

    /** Тело ответа. */
    private String responseBody;


}
