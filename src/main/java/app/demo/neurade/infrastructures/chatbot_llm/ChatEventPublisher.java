package app.demo.neurade.infrastructures.chatbot_llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChatEventPublisher {

    private static final Duration COMPLETED_JOB_TTL = Duration.ofMinutes(2);
    private static final String COMPLETED_JOB_KEY_PREFIX = "chat:completed:";

    // jobId → SseEmitter
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public ChatEventPublisher(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public SseEmitter register(String jobId) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(10).toMillis());
        Object finalResult = redisTemplate.opsForValue().get(completedJobKey(jobId));
        if (finalResult != null) {
            try {
                if (finalResult instanceof String jsonString) {
                    finalResult = objectMapper.readValue(jsonString, Object.class);
                }
                emitter.send(SseEmitter.event().name("status").data(finalResult));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }
        emitters.put(jobId, emitter);
        emitter.onCompletion(() -> emitters.remove(jobId));
        emitter.onTimeout(() -> emitters.remove(jobId));
        return emitter;
    }

    public void publish(String jobId, String eventType, Object data) {
        SseEmitter emitter = emitters.get(jobId);
        if (emitter == null) return;
        try {
            emitter.send(SseEmitter.event().name(eventType).data(data));
        } catch (IOException e) {
            emitters.remove(jobId);
        }
    }

    public void complete(String jobId, Object finalData) {
        Object completionPayload = finalData != null ? finalData : Map.of("status", "COMPLETED");
        try {
            String cleanJson = objectMapper.writeValueAsString(completionPayload);
            redisTemplate.opsForValue().set(completedJobKey(jobId), cleanJson, COMPLETED_JOB_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize completion payload for job {}: {}", jobId, e.getMessage());
        }

        SseEmitter emitter = emitters.get(jobId);
        if (emitter != null) {
            emitter.complete();
            emitters.remove(jobId);
        }
    }

    private String completedJobKey(String jobId) {
        return COMPLETED_JOB_KEY_PREFIX + jobId;
    }
}