package app.demo.neurade.controllers;

import app.demo.neurade.domain.mappers.Mapper;
import app.demo.neurade.infrastructures.chatbot_llm.ChatEventPublisher;
import app.demo.neurade.security.CustomUserDetails;
import app.demo.neurade.security.RequireVerified;
import app.demo.neurade.services.ChatbotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/chatbot")
@RequiredArgsConstructor
@RequireVerified
@Tag(name = "Chatbot", description = "Chatbot interaction APIs")
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final Mapper mapper;
    private final ChatEventPublisher chatEventPublisher;

    @Operation(
            summary = "Chat with AI chatbot",
            description = "Send a question to chatbot with optional uploaded files",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Chat response returned successfully"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized"),
                    @ApiResponse(responseCode = "400", description = "Invalid request")
            }
    )
    @PostMapping(
            value = "/chat",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> chat(
            @Parameter(
                    description = "AI instance ID"
            )
            @RequestParam("instanceId") UUID instanceId,

            @Parameter(
                    description = "Conversation ID (optional; omit to start a new conversation)"
            )
            @RequestParam(value = "conversationId", required = false) String conversationId,

            @Parameter(
                    description = "User question"
            )
            @RequestParam("question") String question,

            @Parameter(
                    description = "Optional uploaded files",
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestPart(value = "files", required = false)
            List<MultipartFile> files
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        UUID jobId = chatbotService.enqueueChat(
                userDetails.getUser(),
                instanceId,
                conversationId,
                question,
                files
        );

        return ResponseEntity.ok(
                Map.of(
                        "jobId", jobId,
                        "message", "Chat request enqueued successfully"
                )
        );
    }

    @Operation(
            summary = "Stream chat progress via SSE",
            description = "Open an SSE connection to receive real-time streaming events for a chat job. " +
                    "Call this endpoint right after POST /chat with the returned jobId. " +
                    "Events: progress, stream, stream_end, status, error"
    )
    @GetMapping(value = "/chat/stream/{jobId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@PathVariable String jobId) {
        return chatEventPublisher.register(jobId);
    }

    @GetMapping("/chat/job-status/{jobId}")
    @Deprecated
    public ResponseEntity<?> getChatJobStatus(
            @PathVariable UUID jobId
    ) {
        return ResponseEntity.ok(
                mapper.toDto(chatbotService.getChatJobStatus(jobId))
        );
    }

    @GetMapping("/{conversationId}/history")
    public ResponseEntity<?> getChatHistory(
            @PathVariable String conversationId
    ) {
        return ResponseEntity.ok(
                chatbotService.getChatHistory(conversationId)
        );
    }

    @GetMapping("/conversations")
    public ResponseEntity<?> getUserConversations() {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        return ResponseEntity.ok(
                chatbotService.getUserConversations(userDetails.getUser().getId())
                        .stream()
                        .map(mapper::toDto)
                        .toList()
        );
    }
}
