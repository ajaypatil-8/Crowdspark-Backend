// src/main/java/Crowdspark/Crowdspark/queue/QueuedJob.java
// Feature #36 — Async job queue
//
// payloadJson is deliberately a nested JSON STRING, not a flat
// Map<String,Object>. Round-tripping values like Long/Double/LocalDateTime
// through an untyped Map is a classic Jackson trap: a Long that fits in an
// int range comes back out as Integer, and a LocalDateTime comes back out as
// a plain String, with nothing forcing either back to the right type — easy
// to get a ClassCastException or a silent wrong value. Keeping the payload
// as its own JSON string means the worker deserializes it straight into a
// properly-typed record (see EmailServiceImpl's payload records), so Jackson
// does that type coercion correctly using the target type's own field types.

package Crowdspark.Crowdspark.queue;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueuedJob {
    private String type;
    private String payloadJson;
}
