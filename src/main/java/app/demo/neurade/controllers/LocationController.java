package app.demo.neurade.controllers;

import app.demo.neurade.domain.mappers.Mapper;
import app.demo.neurade.services.LocationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/location")
@Tag(
        name = "Location",
        description = "APIs for accessing location"
)
public class LocationController {

    private final Mapper mapper;
    private final LocationService locationService;

    @GetMapping("/provinces")
    public ResponseEntity<?> getProvinces() {
        return ResponseEntity.ok(
                locationService.getProvinces().stream()
                        .map(mapper::toDto)
                        .toList()
        );
    }

    @GetMapping("province/{provinceId}/communes")
    public ResponseEntity<?> getCommunesByProvince(
            @PathVariable Integer provinceId
    ) {
        return ResponseEntity.ok(
                locationService.getCommunes(provinceId).stream()
                        .map(mapper::toDto)
                        .toList()
        );
    }
}
