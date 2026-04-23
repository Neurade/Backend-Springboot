package app.demo.neurade.infrastructures.repositories;

import app.demo.neurade.domain.models.AIPackage;
import app.demo.neurade.domain.models.chatbot.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface AIPackageRepository extends JpaRepository<AIPackage, Integer> {
    Optional<AIPackage> findByName(String name);

    @Query("""
    SELECT ap.id
        FROM AIPackage ap
        JOIN Conversation c ON c.instance.aiPackage = ap
        WHERE c.id = :#{#conversation.id}
    """
    )
    Optional<String> findAIPackageIdFromConversation(Conversation conversation);

    @Query("""
    SELECT ap.id
        FROM AIPackage ap
        JOIN AIPackageInstance instance ON instance.aiPackage = ap
        WHERE instance.id = :instanceId
""")
    Optional<String> findAIPackageIdFromInstanceId(UUID instanceId);
}
