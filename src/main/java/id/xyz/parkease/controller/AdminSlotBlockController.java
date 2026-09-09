package id.xyz.parkease.controller;

import id.xyz.parkease.domain.CustomerAccount.Role;
import id.xyz.parkease.dto.BlockResponse;
import id.xyz.parkease.dto.CreateBlockRequest;
import id.xyz.parkease.exception.ForbiddenException;
import id.xyz.parkease.exception.UnauthorizedException;
import id.xyz.parkease.security.AuthenticatedPrincipal;
import id.xyz.parkease.security.JwtAuthenticationFilter;
import id.xyz.parkease.service.SlotBlockService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminSlotBlockController {

    private final SlotBlockService slotBlockService;

    public AdminSlotBlockController(SlotBlockService slotBlockService) {
        this.slotBlockService = Objects.requireNonNull(slotBlockService);
    }

    @PostMapping("/slots/{slotId}/blocks")
    public ResponseEntity<BlockResponse> create(
            @PathVariable UUID slotId,
            @Valid @RequestBody CreateBlockRequest request,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = requireOperatorOrAdmin(servletRequest);
        BlockResponse response = slotBlockService.create(slotId, request, principal.customerAccountId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/slots/{slotId}/blocks")
    public List<BlockResponse> list(
            @PathVariable UUID slotId,
            HttpServletRequest servletRequest) {
        requireOperatorOrAdmin(servletRequest);
        return slotBlockService.list(slotId);
    }

    @DeleteMapping("/slot-blocks/{blockId}")
    public BlockResponse delete(
            @PathVariable UUID blockId,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = requireOperatorOrAdmin(servletRequest);
        return slotBlockService.delete(blockId, principal.customerAccountId());
    }

    private AuthenticatedPrincipal requireOperatorOrAdmin(HttpServletRequest servletRequest) {
        Object attribute = servletRequest.getAttribute(JwtAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(attribute instanceof AuthenticatedPrincipal principal)) {
            throw new UnauthorizedException("authentication is required");
        }
        if (principal.role() != Role.OPERATOR && principal.role() != Role.ADMIN) {
            throw new ForbiddenException("operator or admin role is required");
        }
        return principal;
    }
}
