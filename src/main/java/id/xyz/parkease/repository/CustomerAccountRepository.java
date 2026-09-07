package id.xyz.parkease.repository;

import id.xyz.parkease.domain.CustomerAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, UUID> {

    Optional<CustomerAccount> findByUsername(String username);

    boolean existsByUsername(String username);
}
