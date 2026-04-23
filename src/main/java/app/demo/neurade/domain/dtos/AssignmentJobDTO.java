package app.demo.neurade.domain.dtos;

import app.demo.neurade.domain.dtos.messages.MessageStatus;
import app.demo.neurade.infrastructures.chatbot_llm.responses.WorkflowResponse;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentJobDTO {
    private UUID jobId;
    private MessageStatus status;
    private String errorMessage;
    private WorkflowResponse response;
}
