package org.ex9.auditlistener.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.ex9.auditlistener.event.AuditLogDto;
import org.ex9.auditlistener.model.AuditLogEntity;
import org.ex9.auditlistener.repository.AuditMethodRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.UUID;

/**
 * Сервис для обработки и сохранения Audit-логов.
 * @author Краковцев Артём
 */
@Service
@RequiredArgsConstructor
@Log4j2
public class AuditLogService {

    private final AuditMethodRepository auditMethodRepository;

    /**
     * Сохраняет Audit-лог в базе данных.
     * <p>
     * Перед сохранением проверяет, что сообщение с указанным {@code messageId}
     * ещё не обрабатывалось. Если запись уже существует, лог не сохраняется.
     * </p>
     *
     * @param auditLogDto   DTO с данными события
     * @param consumerRecord исходное сообщение Kafka, из которого получены данные
     */
    @Transactional
    public void saveAuditLog(AuditLogDto auditLogDto, ConsumerRecord<String, String> consumerRecord) {
        log.debug("Processing audit log: eventId={}, type={}", auditLogDto.getId(), auditLogDto.getType());

        if (auditMethodRepository.existsByMessageId(auditLogDto.getMessageId())) {
            log.warn("Kafka message already processed: messageId={}", auditLogDto.getMessageId());
            return;
        }

        try {
            var time = parseTimestamp(auditLogDto.getTimestamp());
            AuditLogEntity entity = AuditLogEntity.builder()
                    .auditId(UUID.fromString(auditLogDto.getId()))
                    .eventType(auditLogDto.getType())
                    .messageId(auditLogDto.getMessageId())
                    .method(auditLogDto.getMethodName())
                    .args(Arrays.toString(auditLogDto.getArgs()))
                    .result(auditLogDto.getResult())
                    .error(auditLogDto.getError())
                    .level(auditLogDto.getLogLevel())
                    .timestamp(time)
                    .build();

            auditMethodRepository.save(entity);
            log.info("Audit log saved successfully: eventId={}", auditLogDto.getId());

        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate key on save (race condition), treating as already processed: offset={}", consumerRecord.offset());
        } catch (Exception e) {
            log.error("Error saving audit log: eventId={}", auditLogDto.getId(), e);
            throw new RuntimeException("Failed to save audit log", e);
        }
    }

    private Instant parseTimestamp(String timestamp) {
        if (timestamp == null) {
            return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant();
        }
        try {
            return LocalDateTime.parse(timestamp).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ex) {
                log.warn("Error parsing timestamp: {}, using current time", timestamp, ex);
                return LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant();
            }
        }
    }

}
