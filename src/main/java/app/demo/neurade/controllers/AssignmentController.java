package app.demo.neurade.controllers;

import app.demo.neurade.domain.mappers.Mapper;
import app.demo.neurade.exception.UnauthorizedException;
import app.demo.neurade.security.CustomUserDetails;
import app.demo.neurade.security.RequireVerified;
import app.demo.neurade.services.AssignmentJudgeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequireVerified
@RequestMapping("/api/v1/assignment")
@Tag(
        name = "Assignment",
        description = "APIs for handling assignment judgement and results"
)
public class AssignmentController {

    private final AssignmentJudgeService assignmentJudgeService;
    private final Mapper mapper;

    @Operation(summary = "Judge assignment", description = "Submit assignment answers for asynchronous judging")
    @PostMapping("/judge")
    public ResponseEntity<?> judgeAssignment(
            @Parameter(description = "AI package instance ID")
            @RequestParam UUID instanceId,
            @Parameter(description = "Multipart form-data payload containing answer files")
            MultipartHttpServletRequest request
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (userDetails == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        Map<String, MultipartFile> answers = request.getFileMap();

        Map<String, String> res = assignmentJudgeService.checkAnswers(
                userDetails.getUser(),
                instanceId,
                answers
        );
        return ResponseEntity.ok(
                Map.of(
                        "message", "Assignment answers enqueued for judgement",
                        "jobIds", res
                )
        );
    }

    @Operation(summary = "Get assignment judge job status", description = "Retrieve the processing status of an assignment judging job")
    @GetMapping("/judge/job-status/{jobId}")
    public ResponseEntity<?> getAssignmentJudgeJobStatus(
            @Parameter(description = "Assignment judging job ID")
            @PathVariable UUID jobId
    ) {
        var result = assignmentJudgeService.getAssignmentJob(jobId);
        return ResponseEntity.ok(mapper.toDto(result));
    }

    @Operation(summary = "Get assignment judgement", description = "Retrieve final judgement results for an assignment")
    @GetMapping("/{assignmentId}/judgement")
    public ResponseEntity<?> getAssignmentJudgement(
            @Parameter(description = "Assignment ID")
            @PathVariable String assignmentId
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (userDetails == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        var result = assignmentJudgeService.getJudgementResults(
                userDetails.getUser(),
                UUID.fromString(assignmentId)
        );
        return ResponseEntity.ok(result);
    }
}
