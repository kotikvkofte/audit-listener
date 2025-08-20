package org.ex9.auditlistener.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.ex9.auditlistener.event.HttpLogDto;
import org.ex9.auditlistener.model.HttpLogEntity;
import org.ex9.auditlistener.repository.HttpLogRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Сервис для обработки и сохранения HTTP-логов.
 * @author Краковцев Артём
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HttpLogService {

    private final HttpLogRepository httpLogRepository;

    /**
     * Сохраняет http-лог в базе данных.
     * <p>
     * Перед сохранением проверяет, что сообщение с указанным {@code messageId}
     * ещё не обрабатывалось. Если запись уже существует, лог не сохраняется.
     * </p>
     *
     * @param httpLogDto   DTO с данными события
     * @param consumerRecord исходное сообщение Kafka, из которого получены данные
     */
    @Transactional
    public void saveHttpLog(HttpLogDto httpLogDto, ConsumerRecord<String, String> consumerRecord) {
        log.debug("Processing HTTP log: method={}, url={}, status={}",
                httpLogDto.getMethod(), httpLogDto.getUrl(), httpLogDto.getStatusCode());

        String topic = consumerRecord.topic();
        int partition = consumerRecord.partition();
        long offset = consumerRecord.offset();

        if (httpLogRepository.existsByMessageId(httpLogDto.getMessageId())) {
            log.warn("Kafka message already processed: messageId={}", httpLogDto.getMessageId());
            return;
        }

        try {
            var time = parseTimestamp(httpLogDto.getTimestamp());
            HttpLogEntity entity = HttpLogEntity.builder()
                    .messageId(httpLogDto.getMessageId())
                    .timestamp(time)
                    .direction(httpLogDto.getDirection())
                    .method(httpLogDto.getMethod())
                    .statusCode(httpLogDto.getStatusCode())
                    .url(httpLogDto.getUrl())
                    .requestBody(httpLogDto.getRequestBody())
                    .responseBody(httpLogDto.getResponseBody())
                    .kafkaPartition(partition)
                    .kafkaOffset(offset)
                    .kafkaTopic(topic)
                    .build();

            httpLogRepository.save(entity);
            log.info("HTTP log saved successfully: method={}, url={}", httpLogDto.getMethod(), httpLogDto.getUrl());

        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate key on save (race condition), treating as already processed: offset={}", offset);
        } catch (Exception e) {
            log.error("Error saving HTTP log: method={}, url={}", httpLogDto.getMethod(), httpLogDto.getUrl(), e);
            throw new RuntimeException("Failed to save HTTP log", e);
        }
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(timestamp);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ex) {
                log.warn("Error parsing timestamp: {}, using current time", timestamp, ex);
                return LocalDateTime.now();
            }
        }
    }

}
