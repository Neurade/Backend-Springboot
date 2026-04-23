package app.demo.neurade.services;

import app.demo.neurade.domain.models.User;
import app.demo.neurade.domain.dtos.messages.AssignmentMessage;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

public interface AssignmentJudgeService {
    Map<String, String> checkAnswers(User user, UUID instanceId, Map<String, MultipartFile> answers);
    Map<UUID, String> getJudgementResults(User user, UUID assignmentId);
    void saveJudgement(String judgement, Long studentId, UUID questionId);
    AssignmentMessage getAssignmentJob(UUID jobId);
}
