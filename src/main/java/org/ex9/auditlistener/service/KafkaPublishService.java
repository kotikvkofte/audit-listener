package org.ex9.auditlistener.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@RequiredArgsConstructor
@Component
@Log4j2
public class KafkaPublishService {

    private static final String ERROR_TOPIC = "audit.errors";
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Отправляет ошибку в kafka в соответствующий топик.
     *
     * @param errorMessage сообщение с ошибкой
     */
    public void sendError(String errorMessage) {
        kafkaTemplate.executeInTransaction(ops -> {
            ops.send(ERROR_TOPIC, UUID.randomUUID().toString(), errorMessage);
            return true;
        });
    }

}
