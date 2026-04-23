package app.demo.neurade.domain.models;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "ai_packages_instances")
@Slf4j
public class AIPackageInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(
            name = "ai_package_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_package_instance_ai_package")
    )
    private AIPackage aiPackage;

    public enum Status {
        ACTIVE,
        INACTIVE
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "class_id",
            foreignKey = @ForeignKey(name = "fk_ai_package_instance_class")
    )
    private Classroom classRoom;

    @ManyToOne
    @JoinColumn(
            name = "purchased_by",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_ai_package_instance_user")
    )
    private User buyer;

    @Column(name = "remaining_token", nullable = false)
    private Long remainingToken;

    @Column(name = "purchase_date", nullable = false)
    private LocalDateTime purchaseDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @PrePersist
    public void onCreate() {
        if (this.status == null) {
            this.status = Status.ACTIVE;
        }
    }

    public void deductTokens(Long tokens) {
        if (tokens < 0) {
            throw new IllegalArgumentException("Tokens to deduct must be non-negative");
        }
        log.info("Deducting {} tokens from AIPackageInstance {}", tokens, this.id);
        log.info("Before deduction, remaining tokens: {}", this.remainingToken);
        this.remainingToken -= tokens;
        log.info("After deduction, remaining tokens: {}", this.remainingToken);
    }
}
