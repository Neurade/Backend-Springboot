package app.demo.neurade.infrastructures.chatbot_llm.requests;

import app.demo.neurade.infrastructures.chatbot_llm.responses.WorkflowResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowEvent {
    private String type;
    private String step;
    private String status;
    private String detail;
    private String chunk;
    private String timestamp;
    private WorkflowResponse data;

    @JsonProperty("elapsed_since_start_s")
    private Double elapsedSinceStartS;

    @JsonProperty("step_duration_s")
    private Double stepDurationS;

    @JsonProperty("total_duration_s")
    private Double totalDurationS;
}