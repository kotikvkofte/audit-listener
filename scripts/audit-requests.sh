curl -X PUT "http://localhost:9200/audit-requests" \
  -H 'Content-Type: application/json' \
  -d '{
    "mappings": {
      "properties": {
        "messageId": { "type": "keyword" },
        "timestamp": { "type": "date", "format": "strict_date_optional_time||epoch_millis" },
        "direction": { "type": "keyword" },
        "method": {
          "type": "text",
            "fields": {
              "keyword": {
                "type": "keyword",
                "ignore_above": 256
              }
            }
        },
        "statusCode": { "type": "keyword" },
        "url": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "requestBody": { "type": "text" },
        "responseBody": { "type": "text" }
      }
    }
  }'
