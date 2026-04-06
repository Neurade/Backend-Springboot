package app.demo.neurade.controllers;

import app.demo.neurade.domain.mappers.Mapper;
import app.demo.neurade.exception.UnauthorizedException;
import app.demo.neurade.security.CustomUserDetails;
import app.demo.neurade.security.RequireVerified;
import app.demo.neurade.services.AssignmentJudgeService;
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

    @PostMapping("/judge")
    public ResponseEntity<?> judgeAssignment(
            @RequestParam UUID instanceId,
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

    @GetMapping("/judge/job-status/{jobId}")
    public ResponseEntity<?> getAssignmentJudgeJobStatus(
            @PathVariable UUID jobId
    ) {
        var result = assignmentJudgeService.getAssignmentJob(jobId);
        return ResponseEntity.ok(mapper.toDto(result));
    }

    @GetMapping("/{assignmentId}/judgement")
    public ResponseEntity<?> getAssignmentJudgement(
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
