package com.saga.engine.controller;

import com.saga.engine.dto.*;
import com.saga.engine.entity.User;
import com.saga.engine.repository.UserRepository;
import com.saga.engine.service.SagaTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class SagaTemplateController {

    private final SagaTemplateService sagaTemplateService;
    private final UserRepository userRepository;

    @PostMapping("/publish")
    public ResponseEntity<ApiResponse<TemplateDTO>> publishTemplate(
            @Valid @RequestBody PublishTemplateRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        TemplateDTO template = sagaTemplateService.publishTemplate(request, username);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @PostMapping("/review")
    public ResponseEntity<ApiResponse<TemplateDTO>> reviewTemplate(
            @Valid @RequestBody ReviewTemplateRequest request,
            Authentication authentication) {
        String reviewer = authentication.getName();
        TemplateDTO template = sagaTemplateService.reviewTemplate(request, reviewer);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @PostMapping("/{id}/resubmit")
    public ResponseEntity<ApiResponse<TemplateDTO>> resubmitTemplate(
            @PathVariable Long id,
            @Valid @RequestBody PublishTemplateRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        TemplateDTO template = sagaTemplateService.resubmitTemplate(id, request, username);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<TemplateDTO>>> searchTemplates(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "false") boolean onlyFavorites,
            Authentication authentication) {
        Long userId = null;
        if (authentication != null && authentication.isAuthenticated()) {
            userId = getUserIdFromAuth(authentication);
        }
        Page<TemplateDTO> templates = sagaTemplateService.searchTemplates(keyword, category, page, size, sortBy, userId, onlyFavorites);
        return ResponseEntity.ok(ApiResponse.success(templates));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TemplateDTO>> getTemplateDetail(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = null;
        if (authentication != null && authentication.isAuthenticated()) {
            userId = getUserIdFromAuth(authentication);
        }
        TemplateDTO template = sagaTemplateService.getTemplateDetail(id, userId);
        return ResponseEntity.ok(ApiResponse.success(template));
    }

    @GetMapping("/{name}/versions")
    public ResponseEntity<ApiResponse<List<TemplateDTO>>> getTemplateVersions(@PathVariable String name) {
        List<TemplateDTO> versions = sagaTemplateService.getTemplateVersions(name);
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    @PostMapping("/import")
    public ResponseEntity<ApiResponse<ImportResultDTO>> importTemplate(
            @Valid @RequestBody ImportTemplateRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        Long userId = getUserIdFromAuth(authentication);
        ImportResultDTO result = sagaTemplateService.importTemplate(request, username, userId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/rate")
    public ResponseEntity<ApiResponse<TemplateRatingDTO>> submitRating(
            @Valid @RequestBody RatingRequest request,
            Authentication authentication) {
        Long userId = getUserIdFromAuth(authentication);
        TemplateRatingDTO rating = sagaTemplateService.submitRating(request, userId);
        return ResponseEntity.ok(ApiResponse.success(rating));
    }

    @GetMapping("/{id}/ratings")
    public ResponseEntity<ApiResponse<List<TemplateRatingDTO>>> getTemplateRatings(@PathVariable Long id) {
        List<TemplateRatingDTO> ratings = sagaTemplateService.getTemplateRatings(id);
        return ResponseEntity.ok(ApiResponse.success(ratings));
    }

    @PostMapping("/{id}/favorite")
    public ResponseEntity<ApiResponse<Void>> toggleFavorite(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = getUserIdFromAuth(authentication);
        sagaTemplateService.toggleFavorite(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private Long getUserIdFromAuth(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + username));
    }
}
