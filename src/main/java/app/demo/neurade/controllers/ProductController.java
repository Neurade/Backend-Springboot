package app.demo.neurade.controllers;

import app.demo.neurade.domain.dtos.requests.AIPackageCreationRequest;
import app.demo.neurade.domain.dtos.requests.AIPackagePurchaseRequest;
import app.demo.neurade.domain.dtos.requests.ValidateKeyRequest;
import app.demo.neurade.domain.dtos.requests.ModifyAIPackageInstanceRequest;
import app.demo.neurade.domain.dtos.requests.AIPackageModificationRequest;
import app.demo.neurade.domain.mappers.Mapper;
import app.demo.neurade.domain.models.AIPackage;
import app.demo.neurade.security.CustomUserDetails;
import app.demo.neurade.security.RequireVerified;
import app.demo.neurade.services.AIPackageInstanceService;
import app.demo.neurade.services.AIPackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
@Tag(name = "Product", description = "Product management APIs")
public class ProductController {

    private final AIPackageService aiPackageService;
    private final Mapper mapper;
    private final AIPackageInstanceService aiPackageInstanceService;

    @Operation(summary = "Validate AI provider API key", description = "Validate an API key against the selected AI provider and return available models")
    @PostMapping("ai-model/api-key/validate")
    public ResponseEntity<?> getAIModels(
            @Parameter(description = "Provider and API key payload")
            @Valid @RequestBody ValidateKeyRequest req
    ) {
        return ResponseEntity.ok(
                aiPackageService.validateApiKey(req.getApiKey(), req.getProvider())
        );
    }

    @Operation(summary = "Create AI package", description = "Create a new AI package (admin only)")
    @PostMapping("/ai-package")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createAIPackage(
            @Parameter(description = "AI package creation payload")
            @Valid @RequestBody AIPackageCreationRequest req
    ) {
        AIPackage aiPackage = aiPackageService.createPackage(req);
        return ResponseEntity.ok(
                Map.of(
                        "message", "AI Package created successfully",
                        "data", mapper.toDto(aiPackage)
                )
        );
    }

    @Operation(summary = "Purchase AI package", description = "Purchase an AI package for a class")
    @PostMapping("/ai-package/purchase")
//    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANIZATION', 'TEACHER')" )
    @RequireVerified
    public ResponseEntity<?> purchaseAIPackage(
            @Parameter(description = "Purchase payload including class ID and AI package ID")
            @RequestBody AIPackagePurchaseRequest req
    ) {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(
                Map.of(
                        "message", "AI Package purchased successfully",
                        "data", aiPackageService.purchasePackage(
                                userDetails.getUser(),
                                req.getClassId(),
                                req.getAiPackageId()
                        )
                )
        );
    }

    @Operation(summary = "Get all AI packages", description = "Retrieve all available AI packages")
    @GetMapping("/ai-packages")
    public ResponseEntity<?> getAllAIPackages() {
        return ResponseEntity.ok(
                aiPackageService.getAllPackages()
                        .stream()
                        .map(mapper::toDto)
                        .toList()
        );
    }

    @Operation(summary = "Get AI package by ID", description = "Retrieve details of a specific AI package")
    @GetMapping("/ai-package/{packageId}")
    public ResponseEntity<?> getAIPackageById(
            @Parameter(description = "AI package ID")
            @PathVariable Integer packageId
    ) {
        AIPackage aiPackage = aiPackageService.getPackageById(packageId);
        return ResponseEntity.ok(
                mapper.toDto(aiPackage)
        );
    }

    @Operation(summary = "Get AI package instance by ID", description = "Retrieve details of a specific AI package instance")
    @GetMapping("/ai-package/instance/{instanceId}")
    public ResponseEntity<?> getAIPackageInstanceById(
            @Parameter(description = "AI package instance ID")
            @PathVariable String instanceId
    ) {
        return ResponseEntity.ok(
                aiPackageInstanceService.getInstanceById(java.util.UUID.fromString(instanceId))
        );
    }

    @Operation(summary = "Get AI package instances for current user", description = "Retrieve AI package instances for the authenticated user, optionally filtered by class ID")
    @GetMapping("/ai-package/instance")
    public ResponseEntity<?> getAIPackageInstancesForUser(
            @Parameter(description = "Class ID used to filter instances. If not provided, return personal instances of the requester.")
            @RequestParam("classId") Long classId
            ) {
        CustomUserDetails userDetails = (CustomUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ResponseEntity.ok(
                aiPackageInstanceService.getInstanceForUser(userDetails.getUser().getId(), classId)
        );
    }

    @Operation(summary = "Update AI package", description = "Update an existing AI package (admin only)")
    @PatchMapping("/ai-package/{packageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateAIPackage(
            @Parameter(description = "AI package ID")
            @PathVariable Integer packageId,
            @Parameter(description = "AI package update payload")
            @Valid @RequestBody AIPackageModificationRequest req
    ) {
        aiPackageService.modifyPackage(packageId, req);
        return ResponseEntity.ok(
                Map.of(
                        "message", "AI Package updated successfully"
                )
        );
    }

    @Operation(summary = "Update AI package instance", description = "Update an existing AI package instance")
    @PatchMapping("/ai-package/instance/{instanceId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'ORGANIZATION', 'ADMIN')")
    public ResponseEntity<?> updateAIPackageInstance(
            @Parameter(description = "AI package instance ID")
            @PathVariable String instanceId,
            @Parameter(description = "AI package instance update payload")
            @RequestBody ModifyAIPackageInstanceRequest req
    ) {
        return ResponseEntity.ok(
            Map.of(
                "message", "AI Package Instance updated successfully",
                "data", aiPackageInstanceService.modifyInstance(
                    UUID.fromString(instanceId), req
                )
            )
        );
    }

    @Operation(summary = "Delete AI package", description = "Mark an AI package as inactive (admin only)")
    @DeleteMapping("/ai-package/{packageId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteAIPackage(
            @Parameter(description = "AI package ID")
            @PathVariable Integer packageId
    ) {
        var updated = aiPackageService.setInactive(packageId);
        return ResponseEntity.ok(
            Map.of(
                "message", "AI Package deleted successfully",
                "data", mapper.toDto(updated)
            )
        );
    }

    @Operation(summary = "Delete AI package instance", description = "Mark an AI package instance as inactive (admin only)")
    @DeleteMapping("/ai-package/instance/{instanceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteAIPackageInstance(
            @Parameter(description = "AI package instance ID")
            @PathVariable String instanceId
    ) {
        var updated = aiPackageInstanceService.setInactive(UUID.fromString(instanceId));
        return ResponseEntity.ok(
            Map.of(
                "message", "AI Package Instance deleted successfully",
                "data", updated
            )
        );
    }
}
