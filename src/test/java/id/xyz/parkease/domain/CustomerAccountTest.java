package id.xyz.parkease.domain;

import id.xyz.parkease.domain.CustomerAccount.AccountStatus;
import id.xyz.parkease.domain.CustomerAccount.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CustomerAccountTest {

    private static final String USERNAME = "driver1";
    private static final String PASSWORD_HASH = "hashed-value";

    @Test
    void buildWithRequiredFieldsDefaultsRoleAndStatus() {
        CustomerAccount account = CustomerAccount.builder()
                .username(USERNAME)
                .passwordHash(PASSWORD_HASH)
                .build();

        assertNotNull(account.getId());
        assertEquals(USERNAME, account.getUsername());
        assertEquals(PASSWORD_HASH, account.getPasswordHash());
        assertEquals(Role.CUSTOMER, account.getRole());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertNotNull(account.getCreatedAt());
        assertNotNull(account.getUpdatedAt());
    }

    @Test
    void buildWithExplicitRoleAndStatus() {
        CustomerAccount account = CustomerAccount.builder()
                .username(USERNAME)
                .passwordHash(PASSWORD_HASH)
                .role(Role.OPERATOR)
                .status(AccountStatus.DISABLED)
                .build();

        assertEquals(Role.OPERATOR, account.getRole());
        assertEquals(AccountStatus.DISABLED, account.getStatus());
    }

    @Test
    void generatedIdsAreUnique() {
        UUID first = CustomerAccount.builder().username(USERNAME).passwordHash(PASSWORD_HASH).build().getId();
        UUID second = CustomerAccount.builder().username(USERNAME).passwordHash(PASSWORD_HASH).build().getId();
        assertNotEquals(first, second);
    }

    @Test
    void applyDefaultsFillsNulls() {
        CustomerAccount account = new CustomerAccount(null, USERNAME, PASSWORD_HASH, null, null, null, null);
        account.applyDefaults();
        assertNotNull(account.getId());
        assertEquals(Role.CUSTOMER, account.getRole());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertNotNull(account.getCreatedAt());
        assertNotNull(account.getUpdatedAt());
    }

    @Test
    void applyDefaultsKeepsExistingValues() {
        CustomerAccount account = CustomerAccount.builder()
                .username(USERNAME)
                .passwordHash(PASSWORD_HASH)
                .role(Role.ADMIN)
                .status(AccountStatus.DISABLED)
                .build();
        UUID id = account.getId();

        account.applyDefaults();

        assertEquals(id, account.getId());
        assertEquals(Role.ADMIN, account.getRole());
        assertEquals(AccountStatus.DISABLED, account.getStatus());
    }
}
