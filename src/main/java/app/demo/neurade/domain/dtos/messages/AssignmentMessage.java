package app.demo.neurade.domain.dtos.messages;

import app.demo.neurade.infrastructures.chatbot_llm.responses.WorkflowResponse;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.util.UUID;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AssignmentMessage implements RabbitMessage {
    private UUID id;
    private Long userId;
    private String answerImageUrl;
    private String apiKey;
    private UUID questionId;
    private String errorMessage;
    private MessageStatus status;
    private WorkflowResponse response;
    private UUID instanceId;

    @Override
    @JsonIgnore
    public String getPrefix() {
        return "assignment:";
    }
}
