package app.demo.neurade.domain.mappers;

import app.demo.neurade.domain.dtos.*;
import app.demo.neurade.domain.models.assignment.Assignment;
import app.demo.neurade.domain.models.assignment.AssignmentQuestion;
import app.demo.neurade.domain.models.chatbot.Conversation;
import app.demo.neurade.domain.models.chatbot.QAEntry;
import app.demo.neurade.domain.models.chatbot.QuestionAsset;
import app.demo.neurade.domain.dtos.messages.AssignmentMessage;
import app.demo.neurade.domain.dtos.messages.ChatbotChatMessage;
import app.demo.neurade.infrastructures.chatbot_llm.responses.VerifyKeyResponse;
import app.demo.neurade.domain.models.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class Mapper {
    public UserDTO toDto(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .verified(user.getVerified())
                .role(RoleType.getRoleById(user.getRole().getId()).getRoleName())
                .build();
    }

    public UserInfoDTO toDto(UserInformation userInformation) {
        return UserInfoDTO.builder()
                .firstName(userInformation.getFirstName())
                .lastName(userInformation.getLastName())
                .cityCode(userInformation.getCity().getCode())
                .subDistrictCode(userInformation.getSubDistrict().getCode())
                .addressDetail(userInformation.getAddressDetail())
                .school(userInformation.getSchool())
                .grade(userInformation.getGrade())
                .bio(userInformation.getBio())
                .favoriteSubjects(userInformation.getFavoriteSubjects())
                .dateOfBirth(userInformation.getDateOfBirth())
                .avatarUrl(userInformation.getAvatarImage())
                .build();
    }

    public UserAndInfoDTO toDto(User user, UserInformation userInformation) {
        return UserAndInfoDTO.builder()
                .user(toDto(user))
                .info(toDto(userInformation))
                .build();
    }

    public ClassDTO toDto(Classroom classroom) {
        return ClassDTO.builder()
                .classId(classroom.getId())
                .name(classroom.getName())
                .creatorId(classroom.getCreator().getId())
                .description(classroom.getDescription())
                .build();
    }

    public ValidateKeyDTO toDto(VerifyKeyResponse response) {
        return ValidateKeyDTO.builder()
                .isValid(response.isValid())
                .models(response.getModels())
                .errorMessage(response.getErrorMessage())
                .build();
    }

    public AIPackageInstanceDTO toDto(AIPackageInstance aiPackageInstance) {
        AIPackage aiPackage = aiPackageInstance.getAiPackage();
        if (aiPackage == null) {
            throw new RuntimeException("When mapping AIPackageInstance to DTO, the associated AIPackage is null");
        }
        var builder = AIPackageInstanceDTO.builder()
                .instanceId(aiPackageInstance.getId())
                .aiPackageId(aiPackageInstance.getAiPackage().getId())
                .purchaserId(aiPackageInstance.getBuyer().getId())
                .remainingToken(aiPackageInstance.getRemainingToken())
                .totalToken(aiPackage.getTotalToken())
                .purchaseDate(aiPackageInstance.getPurchaseDate())
                .status(aiPackageInstance.getStatus())
                .expiryDate(aiPackageInstance.getExpiryDate());

        if (aiPackageInstance.getClassRoom() != null) {
            builder.classId(aiPackageInstance.getClassRoom().getId());
        }

        return builder.build();
    }

    public AIPackageDTO toDto(AIPackage aiPackage) {
        return AIPackageDTO.builder()
                .id(aiPackage.getId())
                .name(aiPackage.getName())
                .price(aiPackage.getPrice())
                .totalToken(aiPackage.getTotalToken())
                .tokenRateLimit(aiPackage.getTokenRateLimit())
                .model(aiPackage.getModel())
                .durationInDays(aiPackage.getDurationInDays())
                .description(aiPackage.getDescription())
                .status(aiPackage.getStatus())
                .build();
    }

    public AssignmentDTO toDto(Assignment assignment) {
        return AssignmentDTO.builder()
                .id(assignment.getId())
                .classId(assignment.getClassroom().getId())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .deadline(assignment.getDeadline())
                .build();
    }

    public AssignmentDTO toDto(Assignment assignment, List<AssignmentQuestion> questions, boolean showCorrectAnswers) {
        var builder = AssignmentDTO.builder()
                .id(assignment.getId())
                .classId(assignment.getClassroom().getId())
                .title(assignment.getTitle())
                .description(assignment.getDescription())
                .deadline(assignment.getDeadline())
                .questions(
                        questions.stream()
                                .map(q -> toDto(q, showCorrectAnswers))
                                .toList()
                );

        return builder.build();
    }

    public AssignmentQuestionDTO toDto(AssignmentQuestion assignmentQuestion, boolean showCorrectAnswer) {
        var builder = AssignmentQuestionDTO.builder()
                .id(assignmentQuestion.getId())
                .questionKey(assignmentQuestion.getQuestionKey())
                .questionType(assignmentQuestion.getQuestionType())
                .questionUrl(assignmentQuestion.getQuestionImageUrl())
                .optionUrls(assignmentQuestion.getAnswerImageUrls())
                .explainUrl(assignmentQuestion.getExplainImageUrl());

        if (showCorrectAnswer) {
            builder.answer(assignmentQuestion.getCorrectAnswer());
        }

        return builder.build();
    }

    public ChatHistoryEntryAssetDTO toDto(QuestionAsset asset) {
        return ChatHistoryEntryAssetDTO.builder()
                .type(asset.getType())
                .objectUrl(asset.getObjectUrl())
                .mimeType(asset.getMimeType())
                .orderIndex(asset.getOrderIndex())
                .build();
    }

    public ChatHistoryEntryDTO toDto(QAEntry entry) {
        return ChatHistoryEntryDTO.builder()
                .question(entry.getQuestionText())
                .answer(entry.getAnswer())
                .timestamp(entry.getCreatedAt())
                .assets(
                        entry.getAssets().stream()
                                .map(this::toDto)
                                .toList()
                )
                .build();
    }

    public CommuneDTO toDto(Commune commune) {
        return CommuneDTO.builder()
                .id(commune.getId())
                .name(commune.getFullName())
                .code(commune.getCode())
                .province(toDto(commune.getProvince()))
                .build();
    }

    public ProvinceDTO toDto(Province province) {
        return ProvinceDTO.builder()
                .id(province.getId())
                .name(province.getFullName())
                .code(province.getCode())
                .build();
    }

    public ConversationDTO toDto(Conversation conversation) {
        return ConversationDTO.builder()
                .id(conversation.getId())
                .createdAt(conversation.getCreatedAt())
                .build();
    }

    public ChatbotChatJobDTO toDto(ChatbotChatMessage chatbotChatMessage) {
        return ChatbotChatJobDTO.builder()
                .jobId(chatbotChatMessage.getId())
                .status(chatbotChatMessage.getStatus())
                .errorMessage(chatbotChatMessage.getErrorMessage())
                .response(chatbotChatMessage.getResponse())
                .build();
    }

    public AssignmentJobDTO toDto(AssignmentMessage assignmentMessage) {
        return AssignmentJobDTO.builder()
                .jobId(assignmentMessage.getId())
                .status(assignmentMessage.getStatus())
                .errorMessage(assignmentMessage.getErrorMessage())
                .response(assignmentMessage.getResponse())
                .build();
    }
}
