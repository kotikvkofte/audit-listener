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
 * DTO для передачи данных о событии логирования.
 * @author Краковцев Артём
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogDto implements LogDto {


    @JsonProperty(required = true)
    private String messageId;

    /** Корреляционный идентификатор события. */
    @JsonProperty(required = true)
    private String id;

    /** Тип события (START/END/ERROR). */
    @JsonProperty(required = true)
    private String type;

    /** Название метода (className.methodName). */
    @JsonProperty(required = true)
    private String methodName;

    /** Аргументы метода (для события START). */
    private Object[] args;

    /** Результат выполнения метода (для события END). */
    private String result;

    /** Текст ошибки (для события ERROR). */
    private String error;

    /** Уровень логирования (INFO, DEBUG и т.д.). */
    @JsonProperty(required = true)
    private String logLevel;

    /** Время события. По умолчанию текущая дата и время. */
    @Builder.Default
    @JsonProperty(required = true)
    private String timestamp = LocalDateTime.now().toString();

}
