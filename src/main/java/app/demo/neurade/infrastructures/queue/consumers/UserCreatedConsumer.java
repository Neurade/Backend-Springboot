package app.demo.neurade.infrastructures.queue.consumers;

import app.demo.neurade.domain.dtos.messages.UserCreatedMessage;
import app.demo.neurade.services.AIPackageInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserCreatedConsumer {

    private final AIPackageInstanceService aiPackageInstanceService;

    @Async
    @EventListener
    public void handleUserCreated(UserCreatedMessage message) {
        log.info("Received user created message for user: {} (source: {})",
                message.getEmail(), message.getSource());

        try {
            switch (message.getSource()) {
                case REGISTRATION -> handleRegistrationUser(message);
                case OAUTH        -> handleOAuthUser(message);
                case ADMIN_CREATION -> handleAdminCreatedUser(message);
            }
            log.info("Successfully processed user created message for: {}", message.getEmail());
        } catch (Exception e) {
            log.error("Error processing user created message for: {}", message.getEmail(), e);
            throw e;
        }
    }

    private void handleRegistrationUser(UserCreatedMessage message) {
        log.info("Processing registration user: {}", message.getEmail());
        createFreeAIInstance(message);
    }

    private void handleOAuthUser(UserCreatedMessage message) {
        log.info("Processing OAuth user: {}", message.getEmail());
        createFreeAIInstance(message);
    }

    private void handleAdminCreatedUser(UserCreatedMessage message) {
        log.info("Processing admin-created user: {}", message.getEmail());
        createFreeAIInstance(message);
    }

    private void createFreeAIInstance(UserCreatedMessage message) {
        log.info("Creating free AI instance for user: {}", message.getEmail());
        aiPackageInstanceService.createFreeInstanceForUser(message.getUserId());
        log.info("Free AI instance created for user: {}", message.getEmail());
    }
}
