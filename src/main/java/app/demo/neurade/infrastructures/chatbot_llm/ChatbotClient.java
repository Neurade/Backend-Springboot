package app.demo.neurade.infrastructures.chatbot_llm;

import app.demo.neurade.infrastructures.chatbot_llm.requests.WorkflowRequest;
import app.demo.neurade.infrastructures.chatbot_llm.responses.WorkflowResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;

@Component
@Slf4j
public class ChatbotClient {

    private final WebClient webClient;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.timeout}")
    private int timeoutSeconds;

    public ChatbotClient(@Value("${llm.qa.endpoint}") String workflowUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(workflowUrl)
                .codecs(configurer ->
                        configurer.defaultCodecs().maxInMemorySize(20 * 1024 * 1024) // 20MB
                )
                .build();
    }

    public WorkflowResponse callWorkflow(
            List<WorkflowRequest.Query> queries,
            List<String> assetUrls,
            String workflowId,
            WorkflowType type
    ) {
        WorkflowRequest request = WorkflowRequest.builder()
                .files(assetUrls)
                .queries(queries)
                .build();

        try {
            String uri = type == WorkflowType.CHAT ? "/{workflowId}/normal" : "/{workflowId}/scoring";
            log.info("Calling LLM workflow at URI: \"{}\" with workflow ID: {}", uri, workflowId);
            WorkflowResponse response = webClient.post()
                    .uri(uri, workflowId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(WorkflowResponse.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (response == null) {
                throw new IllegalStateException("Invalid response from LLM");
            }

            return response;

        } catch (WebClientResponseException ex) {
            log.error("LLM returned error: " + ex.getMessage());
            throw new RuntimeException("LLM error: " + ex.getMessage(), ex);

        } catch (WebClientRequestException ex) {
            throw new RuntimeException("LLM timeout or connection error", ex);
        }
    }
}
