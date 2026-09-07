package id.xyz.parkease.repository;

import id.xyz.parkease.domain.AuditRecord;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {
}
