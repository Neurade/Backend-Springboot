package app.demo.neurade.controllers;

import app.demo.neurade.domain.dtos.AssignmentDTO;
import app.demo.neurade.domain.dtos.AssignmentQuestionDTO;
import app.demo.neurade.domain.dtos.ClassDTO;
import app.demo.neurade.domain.dtos.requests.AssignmentCreationRequest;
import app.demo.neurade.domain.dtos.requests.ClassCreationRequest;
import app.demo.neurade.domain.dtos.requests.UserInstanceUsageCreationRequest;
import app.demo.neurade.domain.mappers.Mapper;
import app.demo.neurade.domain.models.Classroom;
import app.demo.neurade.security.CustomUserDetails;
import app.demo.neurade.services.AssignmentService;
import app.demo.neurade.services.ClassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/class")
@RequiredArgsConstructor
@Tag(
        name = "Class",
        description = "APIs for managing classes"
)
public class ClassController {

    private final ClassService classService;
    private final Mapper mapper;
    private final AssignmentService assignmentService;

    @Operation(
            summary = "Create a new class",
            description = """
            Create a new classroom.
            
            Permissions:
            - ADMIN
            - TEACHER
            """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Class created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ClassDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - missing or invalid JWT"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - insufficient role"
            )
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping()
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZATION', 'TEACHER')")
    public ResponseEntity<?> createClass(
            @Parameter(description = "Class creation payload")
            @RequestBody ClassCreationRequest req
    ) {
        CustomUserDetails userDetails =
                (CustomUserDetails) SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        Classroom newClass =
                classService.createClass(userDetails.getUser(), req);

        return ResponseEntity.ok(
                Map.of(
                        "message", "Class created successfully",
                        "data", mapper.toDto(newClass)
                )
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZATION', 'TEACHER')")
    @Operation(summary = "Get managed classes", description = "Retrieve all classes managed by the authenticated user")
    public ResponseEntity<?> getAllClassesUnderManagement() {
        CustomUserDetails userDetails =
                (CustomUserDetails) SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal();

        return ResponseEntity.ok(
                classService.getAllClassesUnderManagement(userDetails.getUser())
                        .stream()
                        .map(mapper::toDto)
                        .toList()
        );
    }

    @PostMapping("/{classId}/assignment")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZATION', 'TEACHER')")
    @Operation(summary = "Create assignment", description = "Create a new assignment in a class")
    public ResponseEntity<?> createAssignment(
            @Parameter(description = "Class ID")
            @PathVariable("classId") String classId,
            @Parameter(description = "Assignment creation payload")
            @RequestBody AssignmentCreationRequest req
            ) {
        AssignmentDTO dto = assignmentService.createAssignment(
                Long.parseLong(classId),
                req
        );
        return ResponseEntity.ok(
                Map.of(
                        "message", "Assignment created successfully",
                        "data", dto
                )
        );
    }

    @PostMapping("/{classId}/assignment/{assignmentId}/question")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZATION', 'TEACHER')")
    @Operation(summary = "Add assignment question files", description = "Upload files and generate questions for an assignment")
    public ResponseEntity<?> addFileToAssignment(
            @Parameter(description = "Class ID")
            @PathVariable("classId") String classId,
            @Parameter(description = "Assignment ID")
            @PathVariable("assignmentId") UUID assignmentId,
            @Parameter(description = "Question source files")
            @RequestPart("files") List<MultipartFile> files
    ) {
        List<AssignmentQuestionDTO> dtos = assignmentService.createAndProcessPDF(assignmentId, files);
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{classId}/assignment/{assignmentId}")
    @Operation(summary = "Get assignment by ID", description = "Retrieve assignment details for a class")
    public ResponseEntity<?> getAssignment(
            @Parameter(description = "Class ID")
            @PathVariable("classId") String classId,
            @Parameter(description = "Assignment ID")
            @PathVariable("assignmentId") UUID assignmentId
    ) {
        AssignmentDTO dto = classService.getAssignment(
                assignmentId
        );
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/{classId}")
    @Operation(summary = "Get class by ID", description = "Retrieve class details by class ID")
    public ResponseEntity<?> getClass(
            @Parameter(description = "Class ID")
            @PathVariable("classId") String classId
    ) {
        Classroom classroom = classService.getClass(
                Long.parseLong(classId)
        );
        return ResponseEntity.ok(mapper.toDto(classroom));
    }

    @GetMapping("/{classId}/participants")
    @Operation(summary = "Get class participants", description = "Retrieve all participants in a class")
    public ResponseEntity<?> getClassParticipants(
            @Parameter(description = "Class ID")
            @PathVariable("classId") String classId
    ) {
        List<?> participants = classService.getParticipantsInClass(
                Long.parseLong(classId)
        );
        return ResponseEntity.ok(participants);
    }

    @PostMapping("/{classId}/invite")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZATION', 'TEACHER')")
    @Operation(summary = "Invite users to class", description = "Add users to a class by providing user IDs")
    public ResponseEntity<?> inviteUsersToClass(
            @Parameter(description = "Class ID")
            @PathVariable("classId") String classId,
            @Parameter(description = "List of user IDs to invite")
            @RequestBody List<Long> inviteIds
    ) {
        classService.addParticipants(Long.parseLong(classId), inviteIds);
        return ResponseEntity.ok(
                Map.of(
                        "message", "Users invited successfully"
                )
        );
    }

    @PostMapping("/{classId}/instance-usage-limit")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZATION', 'TEACHER')")
    @Operation(summary = "Set class instance usage limit", description = "Configure AI instance usage limits for users in a class")
    public ResponseEntity<?> setClassInstanceUsageLimit(
            @Parameter(description = "Class ID")
            @PathVariable("classId") String classId,
            @Parameter(description = "Instance usage limit payload")
            @RequestBody UserInstanceUsageCreationRequest req
    ) {
        classService.setClassInstanceUsageLimit(
                Long.parseLong(classId),
                req
        );
        return ResponseEntity.ok(
                Map.of(
                        "message", "Class instance usage limit set successfully"
                )
        );
    }

    @GetMapping("/all")
    @Operation(summary = "Get all classes", description = "Retrieve all classes in the system")
    public ResponseEntity<?> getAllClasses() {
        return ResponseEntity.ok(
                classService.getAllClasses()
                        .stream()
                        .map(mapper::toDto)
                        .toList()
        );
    }
}
