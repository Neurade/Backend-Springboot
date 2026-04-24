package app.demo.neurade.services.impl;

import app.demo.neurade.domain.dtos.ChatHistoryEntryDTO;
import app.demo.neurade.domain.dtos.ChatPrepareDTO;
import app.demo.neurade.domain.mappers.Mapper;
import app.demo.neurade.domain.models.User;
import app.demo.neurade.domain.models.chatbot.Conversation;
import app.demo.neurade.domain.models.chatbot.QAEntry;
import app.demo.neurade.domain.dtos.messages.ChatbotChatMessage;
import app.demo.neurade.domain.dtos.messages.MessageStatus;
import app.demo.neurade.infrastructures.repositories.ConversationRepository;
import app.demo.neurade.infrastructures.repositories.QAEntryRepository;
import app.demo.neurade.services.ChatbotService;
import app.demo.neurade.services.ChatbotPersistenceService;
import app.demo.neurade.services.JobStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatbotServiceImpl implements ChatbotService {

    private final QAEntryRepository qaEntryRepository;
    private final ChatbotPersistenceService chatbotPersistenceService;
    private final ConversationRepository conversationRepository;
    private final Mapper mapper;
    private final JobStatusService jobStatusService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public List<ChatHistoryEntryDTO> getChatHistory(String conversationId) {
        if (!conversationRepository.existsById(conversationId)) {
            throw new IllegalArgumentException("Conversation not found with id: " + conversationId);
        }
        List<QAEntry> entries = qaEntryRepository.findAllByConversationIdWithAssets(conversationId);
        return entries.stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public List<Conversation> getUserConversations(Long userId) {
        return conversationRepository.findAllByUser_Id(userId);
    }

    @Override
    public UUID enqueueChat(
            User user,
            UUID instanceId,
            String conversationId,
            String question,
            List<MultipartFile> files
    ) {
        ChatPrepareDTO prepareResult = chatbotPersistenceService.prepareChat(
                user,
                instanceId,
                conversationId,
                question,
                files
        );

        ChatbotChatMessage chatJob = createNewChatJob(
                user,
                prepareResult.instanceId(),
                prepareResult.conversation().getId(),
                prepareResult.apiKey(),
                prepareResult.qaEntryId(),
                question,
                prepareResult.assetUrls()
        );

        chatJob.setStatus(MessageStatus.QUEUED);
        jobStatusService.saveJob(chatJob);

        eventPublisher.publishEvent(chatJob);

        log.info("Enqueued ChatbotChatJob with ID: {}", chatJob.getId());
        return chatJob.getId();
    }

    private ChatbotChatMessage createNewChatJob(
            User user,
            UUID instanceId,
            String conversationId,
            String apiKey,
            Long qaEntryId,
            String question,
            List<String> fileUrls
    ) {
        return ChatbotChatMessage.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .instanceId(instanceId)
                .conversationId(conversationId)
                .qaEntryId(qaEntryId)
                .apiKey(apiKey)
                .question(question)
                .fileUrls(fileUrls)
                .build();
    }

    @Override
    public ChatbotChatMessage getChatJobStatus(UUID jobId) {
        return jobStatusService.getJob(jobId, ChatbotChatMessage.class);
    }
}
