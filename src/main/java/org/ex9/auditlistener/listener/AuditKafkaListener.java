package org.ex9.auditlistener.listener;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.ex9.auditlistener.event.AuditLogDto;
import org.ex9.auditlistener.event.HttpLogDto;
import org.ex9.auditlistener.service.AuditLogService;
import org.ex9.auditlistener.service.HttpLogService;
import org.ex9.auditlistener.service.KafkaPublishService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

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
    private final KafkaPublishService kafkaPublishService;

    @PostConstruct
    public void configureObjectMapper() {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    /**
     * Обрабатывает сообщение Kafka с логами.
     * <p>
     * Определяет тип лога (Audit или HTTP) по содержимому и передаёт в соответствующий сервис для сохранения.
     * </p>
     *
     * @param consumerRecord сообщение Kafka
     */
    @KafkaListener(topics = "${audit.kafka.topic.methodsTopic:audit.methods}", containerFactory = "kafkaListenerContainerFactory")
    public void handleAuditMethods(ConsumerRecord<String, String> consumerRecord) {
        String event = consumerRecord.value();

        log.debug("Processing message from topic: {}, partition: {}, offset: {}",
                consumerRecord.topic(),
                consumerRecord.partition(),
                consumerRecord.offset());
        try {
            AuditLogDto auditLogDto = objectMapper.readValue(event, AuditLogDto.class);

            auditLogService.saveAuditLog(auditLogDto, consumerRecord);
            log.warn("Audit message already processed: eventId={}", auditLogDto.getId());

        } catch (Exception e) {
            String errorMessage = String.format("Parsing or validation error: %s, %s", event, e.getMessage());
            log.error(errorMessage);
            kafkaPublishService.sendError(errorMessage);
        }
    }

    /**
     * Обрабатывает сообщение Kafka с логами.
     * <p>
     * Определяет тип лога (Audit или HTTP) по содержимому и передаёт в соответствующий сервис для сохранения.
     * </p>
     *
     * @param consumerRecord сообщение Kafka
     */
    @KafkaListener(topics = "${audit.kafka.topic.requestsTopic:audit.requests}", containerFactory = "kafkaListenerContainerFactory")
    public void handleAuditRequests(ConsumerRecord<String, String> consumerRecord) {
        String event = consumerRecord.value();

        log.debug("Processing message from topic: {}, partition: {}, offset: {}",
                consumerRecord.topic(),
                consumerRecord.partition(),
                consumerRecord.offset());
        try {
            HttpLogDto httpLogDto = objectMapper.readValue(event, HttpLogDto.class);

            httpLogService.saveHttpLog(httpLogDto, consumerRecord);
            log.info("HTTP log processed successfully: method={}, url={}",
                    httpLogDto.getMethod(), httpLogDto.getUrl());

        } catch (Exception e) {
            String errorMessage = String.format("Parsing or validation error: %s, %s", event, e.getMessage());
            log.error(errorMessage);
            kafkaPublishService.sendError(errorMessage);
        }
    }

}
