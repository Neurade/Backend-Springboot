package app.demo.neurade.services.impl;

import app.demo.neurade.configs.RabbitMQConfig;
import app.demo.neurade.domain.models.StudentAnswer;
import app.demo.neurade.domain.models.User;
import app.demo.neurade.domain.models.assignment.AssignmentQuestion;
import app.demo.neurade.domain.dtos.messages.AssignmentMessage;
import app.demo.neurade.domain.dtos.messages.MessageStatus;
import app.demo.neurade.infrastructures.repositories.AssignmentQuestionRepository;
import app.demo.neurade.infrastructures.repositories.StudentAnswerRepository;
import app.demo.neurade.services.AssignmentJudgeService;
import app.demo.neurade.services.FileService;
import app.demo.neurade.services.JobStatusService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentJudgeServiceImpl implements AssignmentJudgeService {

    @PersistenceContext
    private final EntityManager entityManager;
    private final AssignmentQuestionRepository assignmentQuestionRepository;
    private final FileService fileService;
    private final StudentAnswerRepository studentAnswerRepository;
    private final JobStatusService jobStatusService;
    private final RabbitTemplate rabbitTemplate;
    @Value("${llm.api-key}")
    private String apiKey;

    @Override
    @Transactional
    public Map<String, String> checkAnswers(User user, UUID instanceId, Map<String, MultipartFile> answers) {
        Map<String, String> results = new HashMap<>();
        for (var entry : answers.entrySet()) {
            UUID questionId = UUID.fromString(entry.getKey());
            MultipartFile answerImage = entry.getValue();
            log.info("Uploading answer image for user ID: {} with question ID: {}", user.getId(), questionId);
            String imageUrl = fileService.uploadAssignmentAnswers(questionId, List.of(answerImage)).getFirst();
            log.info("Uploaded answer image to URL: {}", imageUrl);
            String newJobId = UUID.randomUUID().toString();

            AssignmentMessage job = AssignmentMessage.builder()
                    .id(UUID.fromString(newJobId))
                    .userId(user.getId())
                    .answerImageUrl(imageUrl)
                    .apiKey(apiKey)
                    .questionId(questionId)
                    .status(MessageStatus.QUEUED)
                    .instanceId(instanceId)
                    .build();

            jobStatusService.saveJob(job);
            log.info("Created AssignmentJob with ID: {} for user ID: {} and question ID: {}", newJobId, user.getId(), questionId);
            results.put(entry.getKey(), newJobId);

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.ASSIGNMENT_EXCHANGE,
                    RabbitMQConfig.ASSIGNMENT_ROUTING_KEY,
                    job
            );
        }
        return results;
    }

    @Transactional
    @Override
    public void saveJudgement(String judgement, Long studentId, UUID questionId) {
        Optional<StudentAnswer> existingAnswer = studentAnswerRepository
                .findByStudent_IdAndQuestion_Id(studentId, questionId);

        if (existingAnswer.isPresent()) {
            StudentAnswer studentAnswer = existingAnswer.get();
            studentAnswer.setJudgement(judgement);
            studentAnswerRepository.save(studentAnswer);
            log.info("Updated judgement for student {} and question {}", studentId, questionId);
        } else {
            User userRef = entityManager.getReference(User.class, studentId);
            AssignmentQuestion questionRef = entityManager.getReference(AssignmentQuestion.class, questionId);
            StudentAnswer studentAnswer = StudentAnswer.builder()
                    .student(userRef)
                    .question(questionRef)
                    .judgement(judgement)
                    .build();
            studentAnswerRepository.save(studentAnswer);
            log.info("Saved new judgement for student {} and question {}", studentId, questionId);
        }
    }

    @Override
    public Map<UUID, String> getJudgementResults(User user, UUID assignmentId) {
        List<StudentAnswer> answers = studentAnswerRepository.findAllByStudent_IdAndQuestion_Assignment_Id(
                user.getId(),
                assignmentId
        );
        Map<UUID, String> result = new HashMap<>();
        for (StudentAnswer answer : answers) {
            result.put(answer.getQuestion().getId(), answer.getJudgement());
        }

        List<AssignmentQuestion> allQuestions = assignmentQuestionRepository.findAllByAssignment_Id(assignmentId);
        for (AssignmentQuestion question : allQuestions) {
            result.putIfAbsent(question.getId(), null);
        }
        return result;
    }

    @Override
    public AssignmentMessage getAssignmentJob(UUID jobId) {
        return jobStatusService.getJob(jobId, AssignmentMessage.class);
    }
}
