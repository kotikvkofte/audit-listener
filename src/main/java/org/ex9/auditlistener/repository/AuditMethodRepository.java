package org.ex9.auditlistener.repository;

import org.ex9.auditlistener.model.AuditLogEntity;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.UUID;

public interface AuditMethodRepository extends ElasticsearchRepository<AuditLogEntity, UUID> {

    boolean existsByMessageId(String messageId);

}
