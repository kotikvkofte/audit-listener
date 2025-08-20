package org.ex9.auditlistener.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.ex9.auditlistener.event.AuditLogDto;
import org.ex9.auditlistener.event.HttpLogDto;
import org.ex9.auditlistener.event.LogDto;
import org.ex9.auditlistener.service.AuditLogService;
import org.ex9.auditlistener.service.HttpLogService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka Listener для получения и обработки сообщений логов.
 * <p>
 * В зависимости от содержимого сообщения преобразует его
 * в {@link AuditLogDto} или {@link HttpLogDto} и передает
 * на сохранение в соответствующий сервис.
 * </p>
 * @author Краковцев Артём
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class AuditKafkaListener {

    private final AuditLogService auditLogService;
    private final HttpLogService httpLogService;
    private final ObjectMapper objectMapper;

    /**
     * Обрабатывает сообщение Kafka с логами.
     * <p>
     * Определяет тип лога (Audit или HTTP) по содержимому и передаёт в соответствующий сервис для сохранения.
     * </p>
     *
     * @param consumerRecord сообщение Kafka
     */
    @KafkaListener(topics = "${audit.kafka.topic:audit-log}", containerFactory = "kafkaListenerContainerFactory")
    @Transactional("transactionManager")
    public void handle(ConsumerRecord<String, String> consumerRecord) {
        String event = consumerRecord.value();

        log.debug("Processing message from topic: {}, partition: {}, offset: {}",
                consumerRecord.topic(),
                consumerRecord.partition(),
                consumerRecord.offset());

        try {
            JsonNode jsonNode = objectMapper.readTree(event);
            LogDto logDto = parseEvent(jsonNode, event);

            if (logDto instanceof HttpLogDto httpLogDto) {
                httpLogService.saveHttpLog(httpLogDto, consumerRecord);
                log.info("HTTP log processed successfully: method={}, url={}",
                        httpLogDto.getMethod(), httpLogDto.getUrl());
            } else if (logDto instanceof AuditLogDto auditLogDto) {
                auditLogService.saveAuditLog(auditLogDto, consumerRecord);
                log.warn("Audit message already processed: eventId={}", auditLogDto.getId());
            }
            else {
                log.warn("Unknown log type received: {}", logDto.getClass().getSimpleName());
            }
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON message: {}", event, e);
            throw new RuntimeException("Failed to parse audit message", e);
        } catch (Exception e) {
            log.error("Error processing message from partition: {}",event, e);
            throw new RuntimeException("Failed to process audit message", e);
        }
    }

    /**
     * Определяет тип лога (Audit или HTTP) по jsonNode
     */
    private LogDto parseEvent(JsonNode jsonNode, String event) throws JsonProcessingException {
        if (jsonNode.has("id") && jsonNode.has("methodName") && jsonNode.has("logLevel")) {
            return objectMapper.readValue(event, AuditLogDto.class);
        } else if (jsonNode.has("direction") && jsonNode.has("method") && jsonNode.has("statusCode")) {
            return objectMapper.readValue(event, HttpLogDto.class);
        } else {
            throw new IllegalArgumentException("Cannot parse log type from message: " + event);
        }
    }

}
