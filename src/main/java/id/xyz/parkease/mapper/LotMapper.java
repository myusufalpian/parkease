package id.xyz.parkease.mapper;

import id.xyz.parkease.domain.ParkingLot;
import id.xyz.parkease.dto.LotResponse;
import org.springframework.stereotype.Component;

@Component
public class LotMapper {

    public LotResponse toResponse(ParkingLot lot) {
        return new LotResponse(lot.getId(), lot.getName(), lot.getLocation(), lot.getTimezone());
    }
}
