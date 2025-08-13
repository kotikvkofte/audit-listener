package org.ex9.auditlistener.repository;

import org.ex9.auditlistener.model.HttpLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Репозиторий для работы с HTTP-логами.
 * @author Краковцев Артём
 */
@Repository
public interface HttpLogRepository extends JpaRepository<HttpLogEntity, Long> {

    boolean existsByKafkaTopicAndKafkaPartitionAndKafkaOffset(String topic, int partition, long offset);

    boolean existsByMessageId(String messageId);

}
