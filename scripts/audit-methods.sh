curl -X PUT "http://localhost:9200/audit-methods" \
  -H 'Content-Type: application/json' \
  -d '{
    "mappings": {
      "properties": {
        "auditId": { "type": "keyword" },
        "messageId": { "type": "keyword" },
        "eventType": { "type": "keyword" },
        "method": {
          "type": "text",
          "fields": {
              "keyword": {
                "type": "keyword",
                "ignore_above": 256
              }
            }
        },
        "args": { "type": "text" },
        "result": { "type": "text" },
        "error": { "type": "text" },
        "level": { "type": "keyword" },
        "timestamp": { "type": "date", "format": "strict_date_optional_time||epoch_millis" }
      }
    }
  }'