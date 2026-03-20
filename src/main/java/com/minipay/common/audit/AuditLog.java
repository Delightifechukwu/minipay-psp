package com.minipay.common.audit;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "entity_type", nullable = false, length = 50) private String entityType;
    @Column(name = "entity_id", nullable = false, length = 100) private String entityId;
    @Column(nullable = false, length = 50) private String action;
    @Column(name = "actor_username", nullable = false, length = 100) private String actorUsername;
    @Column(name = "old_value", columnDefinition = "TEXT") private String oldValue;
    @Column(name = "new_value", columnDefinition = "TEXT") private String newValue;
    @Column(name = "correlation_id", length = 36) private String correlationId;
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default private Instant createdAt = Instant.now();
}
