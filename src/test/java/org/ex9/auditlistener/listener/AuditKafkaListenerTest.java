package org.ex9.auditlistener.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.ex9.auditlistener.event.AuditLogDto;
import org.ex9.auditlistener.event.HttpLogDto;
import org.ex9.auditlistener.service.AuditLogService;
import org.ex9.auditlistener.service.HttpLogService;
import org.ex9.auditlistener.service.KafkaPublishService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditKafkaListenerTest {

    @Mock
    private AuditLogService auditLogService;
    @Mock
    private HttpLogService httpLogService;
    @Mock
    private KafkaPublishService kafkaPublishService;

    @InjectMocks
    private AuditKafkaListener listener;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        listener = new AuditKafkaListener(auditLogService, httpLogService, objectMapper, kafkaPublishService);
    }

    @Test
    void handleAuditLog_shouldCallAuditService() throws Exception {
        AuditLogDto auditLogDto = AuditLogDto.builder()
                .id("123")
                .type("START")
                .methodName("Test.method")
                .build();
        String message = objectMapper.writeValueAsString(auditLogDto);
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("topic", 0, 0, "key", message);

        listener.handleAuditMethods(record);

        verify(auditLogService).saveAuditLog(eq(auditLogDto), eq(record));
        verifyNoInteractions(httpLogService);
    }

    @Test
    void handleHttpLog_shouldCallHttpService() throws Exception {
        HttpLogDto httpLogDto = HttpLogDto.builder()
                .direction("IN")
                .method("GET")
                .statusCode(200)
                .url("/test")
                .build();
        String message = objectMapper.writeValueAsString(httpLogDto);
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("topic", 0, 0, "key", message);

        listener.handleAuditRequests(record);

        verify(httpLogService).saveHttpLog(eq(httpLogDto), eq(record));
        verifyNoInteractions(auditLogService);
    }

    @Test
    void handleInvalidJson_shouldThrowException() {
        String invalidJson = "{invalid-json}";
        ConsumerRecord<String, String> record =
                new ConsumerRecord<>("topic", 0, 0, "key", invalidJson);

        listener.handleAuditRequests(record);
        verify(kafkaPublishService).sendError(anyString());
    }

}