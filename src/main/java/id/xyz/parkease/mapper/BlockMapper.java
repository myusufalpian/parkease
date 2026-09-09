package id.xyz.parkease.mapper;

import id.xyz.parkease.domain.ParkingSlotBlock;
import id.xyz.parkease.dto.BlockResponse;
import org.springframework.stereotype.Component;

@Component
public class BlockMapper {

    public BlockResponse toResponse(ParkingSlotBlock block) {
        return new BlockResponse(
                block.getId(),
                block.getSlot().getId(),
                block.getBlockedStart(),
                block.getBlockedEnd(),
                block.getReason(),
                block.getStatus().name(),
                block.getCreatedAt(),
                block.getUpdatedAt());
    }
}
