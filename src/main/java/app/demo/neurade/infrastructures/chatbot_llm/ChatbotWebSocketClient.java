package app.demo.neurade.infrastructures.chatbot_llm;

import app.demo.neurade.infrastructures.chatbot_llm.requests.WorkflowEvent;
import app.demo.neurade.infrastructures.chatbot_llm.requests.WorkflowRequest;
import app.demo.neurade.infrastructures.chatbot_llm.responses.WorkflowResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Component
@Slf4j
public class ChatbotWebSocketClient {

    private final ObjectMapper objectMapper;
    private final ReactorNettyWebSocketClient wsClient;

    @Value("${llm.ws.endpoint}")
    private String wsUrl;

    @Value("${llm.timeout}")
    private int timeoutSeconds;

    public ChatbotWebSocketClient() {
        this.objectMapper = new ObjectMapper();
        this.wsClient = new ReactorNettyWebSocketClient();
    }

    public WorkflowResponse callWorkflow(
            List<WorkflowRequest.Query> queries,
            List<String> assetUrls,
            String workflowId,
            WorkflowType type,
            Consumer<WorkflowEvent> onEvent
    ) {
        WorkflowRequest request = WorkflowRequest.builder()
                .files(assetUrls)
                .queries(queries)
                .build();

        String path = type == WorkflowType.CHAT
                ? "/%s/normal"
                : "/%s/scoring";

        URI uri = URI.create(wsUrl + String.format(path, workflowId));
        log.info("Connecting to WebSocket at: {}", uri);

        AtomicReference<WorkflowResponse> resultRef = new AtomicReference<>();
        AtomicReference<String> errorRef = new AtomicReference<>();
        StringBuilder streamBuffer = new StringBuilder();

        wsClient.execute(uri, session -> {
                    String payload;
                    try {
                        payload = objectMapper.writeValueAsString(request);
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Failed to serialize request", e));
                    }

                    Mono<Void> send = session.send(
                            Mono.just(session.textMessage(payload))
                    );

                    Flux<Void> receive = session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .flatMap(text -> {
                                try {
                                    WorkflowEvent event = objectMapper.readValue(text, WorkflowEvent.class);
                                    handleEvent(event, streamBuffer, resultRef, errorRef, onEvent);
                                } catch (Exception e) {
                                    log.error("Failed to parse WebSocket event: {}", text, e);
                                }
                                return Mono.empty();
                            })
                            .takeUntil(ignored -> resultRef.get() != null || errorRef.get() != null)
                            .then()
                            .flux();

                    return send.thenMany(receive).then();
                })
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .onErrorMap(ex -> new RuntimeException("WebSocket connection error: " + ex.getMessage(), ex))
                .block();

        if (errorRef.get() != null) {
            throw new RuntimeException("Workflow failed: " + errorRef.get());
        }

        WorkflowResponse result = resultRef.get();
        if (result == null) {
            throw new IllegalStateException("No result received from LLM");
        }

        return result;
    }

    private void handleEvent(
            WorkflowEvent event,
            StringBuilder streamBuffer,
            AtomicReference<WorkflowResponse> resultRef,
            AtomicReference<String> errorRef,
            Consumer<WorkflowEvent> onEvent
    ) {
        // Forward every event to the caller (consumer → SSE publisher)
        try {
            onEvent.accept(event);
        } catch (Exception e) {
            log.warn("onEvent callback threw an exception for event type {}: {}", event.getType(), e.getMessage());
        }

        switch (event.getType()) {
            case "progress" -> log.info("[{}][{}] {}", event.getStep(), event.getStatus(), event.getDetail());

            case "stream" -> {
                streamBuffer.append(event.getChunk());
                log.debug("[{}] chunk: {}", event.getStep(), event.getChunk());
            }

            case "stream_end" -> {
                log.info("[{}] stream complete", event.getStep());
                streamBuffer.setLength(0);
            }

            case "result" -> {
                log.info("Workflow completed in {}s", event.getTotalDurationS());
                resultRef.set(event.getData());
            }

            case "error" -> {
                log.error("Workflow error: {}", event.getDetail());
                errorRef.set(event.getDetail());
            }

            default -> log.warn("Unknown event type: {}", event.getType());
        }
    }
}