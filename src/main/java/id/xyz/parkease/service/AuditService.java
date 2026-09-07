package id.xyz.parkease.service;

import id.xyz.parkease.domain.AuditRecord;
import id.xyz.parkease.repository.AuditRecordRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditRecordRepository auditRecordRepository;
    private final Clock clock;

    public AuditService(AuditRecordRepository auditRecordRepository, Clock clock) {
        this.auditRecordRepository = Objects.requireNonNull(auditRecordRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, String action, String target, String reason, String beforeState, String afterState) {
        AuditRecord auditRecord = AuditRecord.builder()
                .actorId(actorId)
                .action(action)
                .target(target)
                .reason(reason)
                .beforeState(beforeState)
                .afterState(afterState)
                .correlationId(UUID.randomUUID())
                .occurredAt(OffsetDateTime.now(clock))
                .build();
        auditRecordRepository.save(auditRecord);
    }
}
