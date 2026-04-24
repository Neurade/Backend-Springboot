package app.demo.neurade.services.impl;

import app.demo.neurade.domain.dtos.AssignmentDTO;
import app.demo.neurade.domain.dtos.AssignmentQuestionDTO;
import app.demo.neurade.domain.dtos.requests.AssignmentCreationRequest;
import app.demo.neurade.domain.mappers.Mapper;
import app.demo.neurade.domain.models.Classroom;
import app.demo.neurade.domain.models.assignment.Assignment;
import app.demo.neurade.domain.models.assignment.AssignmentQuestion;
import app.demo.neurade.domain.models.assignment.QuestionType;
import app.demo.neurade.infrastructures.assignment_ocr.AssignmentOcrClient;
import app.demo.neurade.infrastructures.assignment_ocr.responses.ExtractResponse;
import app.demo.neurade.infrastructures.repositories.AssignmentRepository;
import app.demo.neurade.infrastructures.repositories.ClassRepository;
import app.demo.neurade.services.AssignmentQuestionPersistenceService;
import app.demo.neurade.services.AssignmentService;
import app.demo.neurade.services.FileService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentServiceImpl implements AssignmentService {

    private final ClassRepository classRepository;
    private final AssignmentRepository assignmentRepository;
    private final Mapper mapper;
    private final AssignmentOcrClient ocrClient;
    private final FileService fileService;
    private final AssignmentQuestionPersistenceService assignmentQuestionPersistenceService;
    private final Executor questionExecutor;

    @Override
    @Transactional
    public AssignmentDTO createAssignment(Long classId, AssignmentCreationRequest req) {
        Classroom classroom = classRepository.findById(classId)
                .orElseThrow(() -> new RuntimeException("Classroom not found"));

        Assignment assignment = Assignment.builder()
                .classroom(classroom)
                .title(req.getTitle())
                .description(req.getDescription())
                .deadline(req.getDeadline())
                .build();

        assignment = assignmentRepository.save(assignment);

        return mapper.toDto(assignment);
    }

    @Override
    public List<AssignmentQuestionDTO> createAndProcessPDF(UUID assignmentId, List<MultipartFile> files) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Assignment not found"));

        MultipartFile file = files.getFirst();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }

        log.info("Processing file {}", file.getOriginalFilename());
        ExtractResponse response = ocrClient.callExtract(file);
        log.info("Received OCR response with {} questions", response.getQuestions().size());

//        List<AssignmentQuestion> result = new ArrayList<>();

//        for (var entry : response.getQuestions().entrySet()) {
//            String key = entry.getKey();
//            List<ExtractResponse.QuestionRegionDTO> regions = entry.getValue();
//
//            if (!isValidQuestionRegion(key, regions)) {
//                continue;
//            }
//
//            ExtractResponse.QuestionRegionDTO region = regions.getFirst();
//
//            AssignmentQuestion question = processQuestionEntry(key, region, response);
//            if (question != null) {
//                result.add(question);
//            }
//        }

        List<CompletableFuture<AssignmentQuestion>> futures = response.getQuestions().entrySet().stream()
                        .map(entry ->
                            CompletableFuture.supplyAsync(() -> {
                                String key = entry.getKey();
                                List<ExtractResponse.QuestionRegionDTO> regions = entry.getValue();

                                if (!isValidQuestionRegion(key, regions)) return null;
                                return processQuestionEntry(key, regions.getFirst(), response);
                            }, questionExecutor)
                        ).toList();

        List<AssignmentQuestion> result = futures
                .stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();

        assignmentQuestionPersistenceService.saveAssignmentWithQuestions(
                assignment,
                result
        );

        return result.stream()
                .map(aq -> mapper.toDto(aq, true))
                .toList();
    }

    private AssignmentQuestion toEntity(
            String key,
            ExtractResponse.QuestionRegionDTO region,
            List<String> optionImageUrls,
            String correctAnswer,
            String questionImageUrl,
            String explainImageUrl
    ) {

        QuestionType type = detectType(correctAnswer);

        return AssignmentQuestion.builder()
                .questionKey(key)
                .questionType(type)
                .questionImageUrl(questionImageUrl)
                .answerImageUrls(optionImageUrls)
                .correctAnswer(correctAnswer)
                .explainImageUrl(explainImageUrl)
                .page(region.getPage())
                .build();
    }

    private String normalizeAnswer(String answer) {
        if (answer == null) return null;
        return answer.trim().toUpperCase(new Locale("vi", "VN"));
    }

    private QuestionType detectType(String correctAnswer) {
        if (correctAnswer == null || correctAnswer.isEmpty()) {
            return QuestionType.SHORT_ANSWER;
        }

        return switch (correctAnswer.trim()) {
            case "A", "B", "C", "D" -> QuestionType.MCQ;
            case "Đ", "S" -> QuestionType.TF;
            default -> QuestionType.SHORT_ANSWER;
        };
    }

    private String uploadBase64ToMinio(String dataUrl) {
        return fileService.uploadBase64Image(dataUrl, "questions");
    }

    private boolean isValidQuestionRegion(String key, List<ExtractResponse.QuestionRegionDTO> regions) {
        if (regions == null || regions.isEmpty()) {
            log.warn("Question {} has no regions, skipping", key);
            return false;
        }
        return true;
    }

    private AssignmentQuestion processQuestionEntry(
            String key,
            ExtractResponse.QuestionRegionDTO region,
            ExtractResponse response
    ) {
        String questionImageUrl = extractQuestionImage(key, region);
        if (questionImageUrl == null) {
            return null;
        }

        List<String> optionImageUrls = getOptionImages(key, response);
        String correctAnswer = getCorrectAnswer(key, response);
        String explainImageUrl = extractExplainImage(key, response);

        return toEntity(
                key,
                region,
                optionImageUrls,
                correctAnswer,
                questionImageUrl,
                explainImageUrl
        );
    }

    private String extractQuestionImage(String key, ExtractResponse.QuestionRegionDTO region) {
        List<Object> coor = region.getCoor();
        if (coor == null || coor.size() < 5) {
            log.warn("Invalid coor for question {}, skipping", key);
            return null;
        }

        String questionBase64 = (String) coor.get(4);
        return uploadBase64ToMinio(questionBase64);
    }

    private List<String> getOptionImages(String key, ExtractResponse response) {
        if (response.getOptions() == null || !response.getOptions().containsKey(key)) {
            return null;
        }
        Map<String, ExtractResponse.AnswerRegionDTO> map = response.getOptions();
        ExtractResponse.AnswerRegionDTO answerRegion = map.get(key);
        if (answerRegion == null) {
            return null;
        }
        List<List<Object>> optionRegions = answerRegion.getOptions();
        List<String> optionImageUrls = new ArrayList<>();
        for (List<Object> optionCoor : optionRegions) {
            if (optionCoor.size() < 11) {
                continue;
            }
            String optionBase64 = (String) optionCoor.get(9);
            String optionImageUrl = uploadBase64ToMinio(optionBase64);
            optionImageUrls.add(optionImageUrl);
        }
        return optionImageUrls;
    }

    private String getCorrectAnswer(String key, ExtractResponse response) {
        String correctAnswer = null;
        if (response.getCorrectOptions() != null) {
            correctAnswer = response.getCorrectOptions().get(key);
        }
        return normalizeAnswer(correctAnswer);
    }

    private String extractExplainImage(String key, ExtractResponse response) {
        if (response.getExplains() == null || !response.getExplains().containsKey(key)) {
            return null;
        }

        List<List<Object>> explainRegions = response.getExplains().get(key);
        if (explainRegions == null || explainRegions.isEmpty()) {
            return null;
        }

        List<Object> explainCoor = explainRegions.getFirst();
        if (explainCoor.size() < 5) {
            return null;
        }

        String explainBase64 = (String) explainCoor.get(4);
        return uploadBase64ToMinio(explainBase64);
    }
}
