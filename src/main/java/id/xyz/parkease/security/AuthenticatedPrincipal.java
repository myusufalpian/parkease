package id.xyz.parkease.security;

import id.xyz.parkease.domain.CustomerAccount.Role;
import java.util.UUID;

public record AuthenticatedPrincipal(UUID customerAccountId, Role role) {
}
