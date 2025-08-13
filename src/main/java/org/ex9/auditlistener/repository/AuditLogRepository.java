package org.ex9.auditlistener.repository;

import org.ex9.auditlistener.model.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Репозиторий для работы с Audit-логами.
 * @author Краковцев Артём
 */
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    boolean existsByKafkaTopicAndKafkaPartitionAndKafkaOffset(String topic, int partition, long offset);
    boolean existsByMessageId(String messageId);

}
