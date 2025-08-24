package org.ex9.auditlistener.repository;

import org.ex9.auditlistener.model.HttpLogEntity;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.UUID;

public interface AuditRequestRepository extends ElasticsearchRepository<HttpLogEntity, UUID> {

    boolean existsByMessageId(String messageId);

}
