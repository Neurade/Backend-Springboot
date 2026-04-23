package app.demo.neurade.domain.models;

import jakarta.persistence.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Table(name = "user_ai_instance_usages")
@Slf4j
public class UserAIInstanceUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_ai_instance_usage_user")
    )
    private User user;

    @ManyToOne
    @JoinColumn(
            name = "instance_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_ai_instance_usage_ai_package_instance", foreignKeyDefinition = "FOREIGN KEY (instance_id) REFERENCES ai_packages_instances(id) ON DELETE CASCADE")
    )
    private AIPackageInstance instance;

    @Column(name = "token_used", nullable = false)
    private Long tokenUsed;

    @Column(name = "limit_token")
    private Long limitToken;

    @Column(name = "rate_limit_token_limit")
    private Long rateLimitTokenLimit;

    @Column(name = "rate_limit_token_count")
    private Long rateLimitTokenCount;

    @Column(name = "rate_limit_duration_days")
    private Integer rateLimitDurationDays;

    @Column(name = "last_reset_time")
    private LocalDateTime lastResetTime;

    @PreUpdate
    public void onUpdate() {
        if (rateLimitTokenLimit == null || rateLimitDurationDays == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        if (lastResetTime == null) {
            lastResetTime = now;
            this.rateLimitTokenCount = 0L;
            return;
        }

        long daysPassed = Duration
                .between(lastResetTime, now)
                .toDays();

        if (daysPassed >= rateLimitDurationDays) {
            this.rateLimitTokenCount = 0L;
            this.lastResetTime = now;
        }
    }

    private boolean isWithinTotalLimit() {
        return tokenUsed < limitToken;
    }

    private boolean isWithinRateLimit() {
        if (rateLimitTokenLimit == null) {
            return true; // No rate limit set
        }
        return rateLimitTokenCount < rateLimitTokenLimit;
    }

    private boolean isInstanceRanOutOfToken() {
        return instance.getRemainingToken() <= 0;
    }

    public boolean canUseThisPackage() {
        return isWithinTotalLimit() && isWithinRateLimit() && !isInstanceRanOutOfToken();
    }

    public void useToken(long num) {
        if (num < 0) {
            throw new IllegalArgumentException("token number must be non-negative");
        }
        log.info("Using {} tokens for user {} on instance {}", num, user.getId(), instance.getId());
        this.tokenUsed += num;
        if (rateLimitTokenLimit != null) {
            this.rateLimitTokenCount += num;
        }
        log.info("{} / {} tokens used (rate limit: {} / {})",
                this.tokenUsed,
                this.limitToken,
                this.rateLimitTokenCount,
                this.rateLimitTokenLimit
        );
    }
}
