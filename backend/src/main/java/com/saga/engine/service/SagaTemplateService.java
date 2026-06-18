package com.saga.engine.service;

import com.saga.engine.dto.*;
import com.saga.engine.entity.SagaDefinition;
import com.saga.engine.entity.SagaTemplate;
import com.saga.engine.entity.TemplateFavorite;
import com.saga.engine.entity.TemplateRating;
import com.saga.engine.entity.User;
import com.saga.engine.enums.TemplateStatus;
import com.saga.engine.repository.SagaDefinitionRepository;
import com.saga.engine.repository.SagaTemplateRepository;
import com.saga.engine.repository.TemplateFavoriteRepository;
import com.saga.engine.repository.TemplateRatingRepository;
import com.saga.engine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaTemplateService {

    private final SagaTemplateRepository sagaTemplateRepository;
    private final TemplateRatingRepository templateRatingRepository;
    private final TemplateFavoriteRepository templateFavoriteRepository;
    private final SagaDefinitionRepository sagaDefinitionRepository;
    private final UserRepository userRepository;

    private static final List<String> ALLOWED_CATEGORIES = Arrays.asList(
            "订单处理", "支付流程", "库存管理", "用户注册", "通知推送", "数据同步", "审批流程", "其他"
    );

    @Transactional
    public TemplateDTO publishTemplate(PublishTemplateRequest request, String username) {
        if (request.getCategoryTags() != null) {
            for (String tag : request.getCategoryTags()) {
                if (!ALLOWED_CATEGORIES.contains(tag)) {
                    throw new RuntimeException("不合法的分类标签: " + tag + ", 允许的标签: " + ALLOWED_CATEGORIES);
                }
            }
            if (request.getCategoryTags().size() > 5) {
                throw new RuntimeException("分类标签最多5个");
            }
        }

        List<String> invalidSteps = validateTemplateUrls(request.getStepDefinition());
        if (!invalidSteps.isEmpty()) {
            throw new RuntimeException("以下步骤包含非template://占位符URL,请替换后再发布: " + String.join(", ", invalidSteps));
        }

        sagaTemplateRepository.findByNameAndVersion(request.getName(), request.getVersion())
                .ifPresent(t -> {
                    throw new RuntimeException("模板 " + request.getName() + " 版本 " + request.getVersion() + " 已存在");
                });

        if (request.getDependencies() != null && !request.getDependencies().isEmpty()) {
            if (request.getDependencies().size() > 3) {
                throw new RuntimeException("依赖模板最多3个");
            }
            for (String depName : request.getDependencies()) {
                List<SagaTemplate> depTemplates = sagaTemplateRepository.findByNameAndStatus(depName, TemplateStatus.PUBLISHED);
                if (depTemplates == null || depTemplates.isEmpty()) {
                    throw new RuntimeException("依赖模板不存在或未上架: " + depName);
                }
            }
        }

        SagaTemplate template = new SagaTemplate();
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setCategoryTags(request.getCategoryTags());
        template.setVersion(request.getVersion());
        template.setStepDefinition(request.getStepDefinition());
        template.setPublisher(username);
        template.setSceneDescription(request.getSceneDescription());
        template.setDependencies(request.getDependencies());
        template.setStatus(TemplateStatus.PENDING_REVIEW);
        template.setDownloadCount(0);

        SagaTemplate saved = sagaTemplateRepository.save(template);
        log.info("Template published: {} version {}, by {}", saved.getName(), saved.getVersion(), username);
        return convertToDTO(saved, null);
    }

    @Transactional
    public TemplateDTO reviewTemplate(ReviewTemplateRequest request, String reviewer) {
        SagaTemplate template = sagaTemplateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("模板不存在: " + request.getTemplateId()));

        if (template.getStatus() != TemplateStatus.PENDING_REVIEW) {
            throw new RuntimeException("只有待审核状态的模板才能审核");
        }

        if (template.getPublisher().equals(reviewer)) {
            throw new RuntimeException("不能审核自己发布的模板");
        }

        if ("APPROVED".equals(request.getResult())) {
            template.setStatus(TemplateStatus.PUBLISHED);
            template.setReviewComment(null);
        } else if ("REJECTED".equals(request.getResult())) {
            template.setStatus(TemplateStatus.REJECTED);
            template.setReviewComment(request.getComment());
        } else if ("REVISION_REQUIRED".equals(request.getResult())) {
            if (request.getComment() == null || request.getComment().length() < 10) {
                throw new RuntimeException("退回修改意见不少于10个字");
            }
            template.setStatus(TemplateStatus.REVISION_REQUIRED);
            template.setReviewComment(request.getComment());
        }
        template.setReviewer(reviewer);
        template.setReviewedAt(LocalDateTime.now());

        SagaTemplate saved = sagaTemplateRepository.save(template);
        log.info("Template {} version {} reviewed by {}: {}", saved.getName(), saved.getVersion(), reviewer, request.getResult());
        return convertToDTO(saved, null);
    }

    @Transactional
    public TemplateDTO resubmitTemplate(Long templateId, PublishTemplateRequest request, String username) {
        SagaTemplate template = sagaTemplateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("模板不存在: " + templateId));

        if (template.getStatus() != TemplateStatus.REVISION_REQUIRED) {
            throw new RuntimeException("只有退回修改状态的模板才能重新提交");
        }

        if (!template.getPublisher().equals(username)) {
            throw new RuntimeException("只有原发布者才能重新提交");
        }

        if (request.getCategoryTags() != null) {
            for (String tag : request.getCategoryTags()) {
                if (!ALLOWED_CATEGORIES.contains(tag)) {
                    throw new RuntimeException("不合法的分类标签: " + tag + ", 允许的标签: " + ALLOWED_CATEGORIES);
                }
            }
            if (request.getCategoryTags().size() > 5) {
                throw new RuntimeException("分类标签最多5个");
            }
        }

        List<String> invalidSteps = validateTemplateUrls(request.getStepDefinition());
        if (!invalidSteps.isEmpty()) {
            throw new RuntimeException("以下步骤包含非template://占位符URL,请替换后再发布: " + String.join(", ", invalidSteps));
        }

        if (request.getDependencies() != null && !request.getDependencies().isEmpty()) {
            if (request.getDependencies().size() > 3) {
                throw new RuntimeException("依赖模板最多3个");
            }
            for (String depName : request.getDependencies()) {
                List<SagaTemplate> depTemplates = sagaTemplateRepository.findByNameAndStatus(depName, TemplateStatus.PUBLISHED);
                if (depTemplates == null || depTemplates.isEmpty()) {
                    throw new RuntimeException("依赖模板不存在或未上架: " + depName);
                }
            }
        }

        template.setDescription(request.getDescription());
        template.setCategoryTags(request.getCategoryTags());
        template.setStepDefinition(request.getStepDefinition());
        template.setSceneDescription(request.getSceneDescription());
        template.setDependencies(request.getDependencies());
        template.setStatus(TemplateStatus.PENDING_REVIEW);
        template.setReviewComment(null);
        template.setReviewer(null);
        template.setReviewedAt(null);

        SagaTemplate saved = sagaTemplateRepository.save(template);
        log.info("Template {} version {} resubmitted by {}", saved.getName(), saved.getVersion(), username);
        return convertToDTO(saved, null);
    }

    public Page<TemplateDTO> searchTemplates(String keyword, String category, int page, int size, String sortBy, Long userId, boolean onlyFavorites) {
        Sort sort;
        if ("downloadCount".equals(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "downloadCount");
        } else if ("rating".equals(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "averageScore", "downloadCount");
        } else {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<SagaTemplate> templatePage;

        if (onlyFavorites && userId != null) {
            if ("rating".equals(sortBy)) {
                templatePage = templateFavoriteRepository.findFavoriteTemplatesByUserIdOrderByRating(
                        userId, keyword, category, pageable);
            } else if (category != null && !category.isEmpty()) {
                templatePage = templateFavoriteRepository.findFavoriteTemplatesByUserIdAndCategory(
                        userId, keyword, category, pageable);
            } else {
                templatePage = templateFavoriteRepository.findFavoriteTemplatesByUserId(
                        userId, keyword, pageable);
            }
        } else {
            if ("rating".equals(sortBy)) {
                if ((category != null && !category.isEmpty()) && (keyword != null && !keyword.isEmpty())) {
                    templatePage = sagaTemplateRepository.searchByKeywordAndCategoryAndStatusOrderByRating(keyword, category, TemplateStatus.PUBLISHED.name(), pageable);
                } else if (keyword != null && !keyword.isEmpty()) {
                    templatePage = sagaTemplateRepository.searchByKeywordAndStatusOrderByRating(keyword, TemplateStatus.PUBLISHED, pageable);
                } else {
                    templatePage = sagaTemplateRepository.findByStatusOrderByRating(TemplateStatus.PUBLISHED, pageable);
                }
            } else {
                if ((category != null && !category.isEmpty()) && (keyword != null && !keyword.isEmpty())) {
                    templatePage = sagaTemplateRepository.searchByKeywordAndCategoryAndStatus(keyword, category, TemplateStatus.PUBLISHED.name(), pageable);
                } else if (keyword != null && !keyword.isEmpty()) {
                    templatePage = sagaTemplateRepository.searchByKeywordAndStatus(keyword, TemplateStatus.PUBLISHED, pageable);
                } else {
                    templatePage = sagaTemplateRepository.findByStatus(TemplateStatus.PUBLISHED, pageable);
                }
            }
        }

        return templatePage.map(t -> convertToDTO(t, userId));
    }

    public TemplateDTO getTemplateDetail(Long id, Long userId) {
        SagaTemplate template = sagaTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模板不存在: " + id));
        return convertToDTO(template, userId);
    }

    public List<TemplateDTO> getTemplateVersions(String name) {
        List<SagaTemplate> templates = sagaTemplateRepository.findByNameOrderByCreatedAtDesc(name);
        return templates.stream()
                .map(t -> convertToDTO(t, null))
                .collect(Collectors.toList());
    }

    @Transactional
    public ImportResultDTO importTemplate(ImportTemplateRequest request, String username, Long userId) {
        SagaTemplate template = sagaTemplateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("模板不存在: " + request.getTemplateId()));

        if (template.getStatus() != TemplateStatus.PUBLISHED) {
            throw new RuntimeException("只有已上架的模板才能导入");
        }

        List<String> requiredPlaceholders = extractPlaceholderUrls(template.getStepDefinition());
        for (String placeholder : requiredPlaceholders) {
            if (request.getUrlMappings().get(placeholder) == null || request.getUrlMappings().get(placeholder).trim().isEmpty()) {
                throw new RuntimeException("占位符 " + placeholder + " 未填写真实服务地址");
            }
        }

        List<String> unmetDependencies = new ArrayList<>();
        if (template.getDependencies() != null && !template.getDependencies().isEmpty()) {
            List<String> userSagaNames = sagaDefinitionRepository.findDistinctNamesByCreatedBy(username);
            for (String depName : template.getDependencies()) {
                boolean found = userSagaNames.stream()
                        .anyMatch(sagaName -> sagaName.contains(depName));
                if (!found) {
                    unmetDependencies.add(depName);
                }
            }
        }

        List<Map<String, Object>> newDefinition = replacePlaceholderUrls(
                template.getStepDefinition(), request.getUrlMappings());

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String sagaName = template.getName() + "-副本-" + timestamp;

        Integer currentMaxVersion = sagaDefinitionRepository.findMaxVersionByName(sagaName);
        SagaDefinition definition = new SagaDefinition();
        definition.setName(sagaName);
        definition.setDescription("从模板 " + template.getName() + " v" + template.getVersion() + " 导入");
        definition.setVersion(currentMaxVersion != null ? currentMaxVersion + 1 : 1);
        definition.setDefinition(newDefinition);
        definition.setGlobalTimeoutSeconds(300);
        definition.setCreatedBy(username);

        SagaDefinition saved = sagaDefinitionRepository.save(definition);

        template.setDownloadCount(template.getDownloadCount() + 1);
        sagaTemplateRepository.save(template);

        log.info("Template {} v{} imported as saga {} by {}", template.getName(), template.getVersion(), sagaName, username);

        SagaDefinitionDTO dto = new SagaDefinitionDTO();
        dto.setId(saved.getId());
        dto.setName(saved.getName());
        dto.setDescription(saved.getDescription());
        dto.setVersion(saved.getVersion());
        dto.setDefinition(saved.getDefinition());
        dto.setGlobalTimeoutSeconds(saved.getGlobalTimeoutSeconds());
        dto.setCreatedBy(saved.getCreatedBy());
        dto.setCreatedAt(saved.getCreatedAt());
        dto.setUpdatedAt(saved.getUpdatedAt());

        ImportResultDTO result = new ImportResultDTO();
        result.setDefinition(dto);
        result.setUnmetDependencies(unmetDependencies);
        return result;
    }

    @Transactional
    public TemplateRatingDTO submitRating(RatingRequest request, Long userId) {
        sagaTemplateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("模板不存在: " + request.getTemplateId()));

        TemplateRating rating = templateRatingRepository.findByTemplateIdAndUserId(request.getTemplateId(), userId)
                .orElse(new TemplateRating());

        rating.setTemplateId(request.getTemplateId());
        rating.setUserId(userId);
        rating.setScore(request.getScore());
        rating.setComment(request.getComment());

        TemplateRating saved = templateRatingRepository.save(rating);
        log.info("User {} rated template {} with score {}", userId, request.getTemplateId(), request.getScore());

        TemplateRatingDTO dto = new TemplateRatingDTO();
        dto.setId(saved.getId());
        dto.setTemplateId(saved.getTemplateId());
        dto.setUserId(saved.getUserId());
        dto.setScore(saved.getScore());
        dto.setComment(saved.getComment());
        dto.setCreatedAt(saved.getCreatedAt());

        userRepository.findById(userId).ifPresent(user -> dto.setUsername(user.getUsername()));
        return dto;
    }

    public List<TemplateRatingDTO> getTemplateRatings(Long templateId) {
        List<TemplateRating> ratings = templateRatingRepository.findByTemplateId(templateId);
        return ratings.stream().map(r -> {
            TemplateRatingDTO dto = new TemplateRatingDTO();
            dto.setId(r.getId());
            dto.setTemplateId(r.getTemplateId());
            dto.setUserId(r.getUserId());
            dto.setScore(r.getScore());
            dto.setComment(r.getComment());
            dto.setCreatedAt(r.getCreatedAt());
            userRepository.findById(r.getUserId()).ifPresent(user -> dto.setUsername(user.getUsername()));
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void toggleFavorite(Long templateId, Long userId) {
        sagaTemplateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("模板不存在: " + templateId));

        Optional<TemplateFavorite> existing = templateFavoriteRepository.findByTemplateIdAndUserId(templateId, userId);
        if (existing.isPresent()) {
            templateFavoriteRepository.delete(existing.get());
            log.info("User {} unfavorited template {}", userId, templateId);
        } else {
            TemplateFavorite favorite = new TemplateFavorite();
            favorite.setTemplateId(templateId);
            favorite.setUserId(userId);
            templateFavoriteRepository.save(favorite);
            log.info("User {} favorited template {}", userId, templateId);
        }
    }

    public boolean isFavorited(Long templateId, Long userId) {
        if (userId == null) return false;
        return templateFavoriteRepository.existsByTemplateIdAndUserId(templateId, userId);
    }

    private List<String> validateTemplateUrls(List<Map<String, Object>> stepDefinition) {
        List<String> invalidSteps = new ArrayList<>();
        if (stepDefinition == null) return invalidSteps;

        for (Map<String, Object> step : stepDefinition) {
            String stepName = (String) step.getOrDefault("name", "未命名步骤");
            Object forwardActionObj = step.get("forwardAction");
            if (forwardActionObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> forwardAction = (Map<String, Object>) forwardActionObj;
                String url = (String) forwardAction.getOrDefault("url", "");
                if (url != null && !url.isEmpty() && !url.startsWith("template://")) {
                    invalidSteps.add(stepName + "(正向操作URL)");
                }
            }
            Object compActionObj = step.get("compensationAction");
            if (compActionObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> compAction = (Map<String, Object>) compActionObj;
                String url = (String) compAction.getOrDefault("url", "");
                if (url != null && !url.isEmpty() && !url.startsWith("template://")) {
                    invalidSteps.add(stepName + "(补偿URL)");
                }
            }
        }
        return invalidSteps;
    }

    private List<String> extractPlaceholderUrls(List<Map<String, Object>> stepDefinition) {
        Set<String> placeholders = new LinkedHashSet<>();
        if (stepDefinition == null) return new ArrayList<>(placeholders);

        for (Map<String, Object> step : stepDefinition) {
            Object forwardActionObj = step.get("forwardAction");
            if (forwardActionObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> forwardAction = (Map<String, Object>) forwardActionObj;
                String url = (String) forwardAction.getOrDefault("url", "");
                if (url != null && url.startsWith("template://")) {
                    placeholders.add(url);
                }
            }
            Object compActionObj = step.get("compensationAction");
            if (compActionObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> compAction = (Map<String, Object>) compActionObj;
                String url = (String) compAction.getOrDefault("url", "");
                if (url != null && url.startsWith("template://")) {
                    placeholders.add(url);
                }
            }
        }
        return new ArrayList<>(placeholders);
    }

    private List<Map<String, Object>> replacePlaceholderUrls(List<Map<String, Object>> stepDefinition,
                                                              Map<String, String> urlMappings) {
        List<Map<String, Object>> newDefinition = new ArrayList<>();
        for (Map<String, Object> step : stepDefinition) {
            Map<String, Object> newStep = new LinkedHashMap<>(step);

            Object forwardActionObj = step.get("forwardAction");
            if (forwardActionObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> forwardAction = new LinkedHashMap<>((Map<String, Object>) forwardActionObj);
                String url = (String) forwardAction.getOrDefault("url", "");
                if (url != null && urlMappings.containsKey(url)) {
                    forwardAction.put("url", urlMappings.get(url));
                }
                newStep.put("forwardAction", forwardAction);
            }

            Object compActionObj = step.get("compensationAction");
            if (compActionObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> compAction = new LinkedHashMap<>((Map<String, Object>) compActionObj);
                String url = (String) compAction.getOrDefault("url", "");
                if (url != null && urlMappings.containsKey(url)) {
                    compAction.put("url", urlMappings.get(url));
                }
                newStep.put("compensationAction", compAction);
            }

            newDefinition.add(newStep);
        }
        return newDefinition;
    }

    private TemplateDTO convertToDTO(SagaTemplate entity, Long userId) {
        TemplateDTO dto = new TemplateDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setCategoryTags(entity.getCategoryTags());
        dto.setVersion(entity.getVersion());
        dto.setStatus(entity.getStatus().name());
        dto.setPublisher(entity.getPublisher());
        dto.setReviewer(entity.getReviewer());
        dto.setStepDefinition(entity.getStepDefinition());
        dto.setDownloadCount(entity.getDownloadCount());
        dto.setSceneDescription(entity.getSceneDescription());
        dto.setDependencies(entity.getDependencies());
        dto.setReviewComment(entity.getReviewComment());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setReviewedAt(entity.getReviewedAt());

        Double avgScore = templateRatingRepository.getAverageScoreByTemplateId(entity.getId());
        dto.setAverageScore(avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0.0);
        dto.setRatingCount(templateRatingRepository.countByTemplateId(entity.getId()));

        dto.setFavorited(isFavorited(entity.getId(), userId));

        if (entity.getDependencies() != null && !entity.getDependencies().isEmpty()) {
            List<TemplateDTO> depTemplates = new ArrayList<>();
            for (String depName : entity.getDependencies()) {
                List<SagaTemplate> deps = sagaTemplateRepository.findByNameAndStatus(depName, TemplateStatus.PUBLISHED);
                if (deps != null && !deps.isEmpty()) {
                    TemplateDTO depDto = new TemplateDTO();
                    depDto.setId(deps.get(0).getId());
                    depDto.setName(deps.get(0).getName());
                    depDto.setVersion(deps.get(0).getVersion());
                    depDto.setDescription(deps.get(0).getDescription());
                    depTemplates.add(depDto);
                }
            }
            dto.setDependencyTemplates(depTemplates);
        }

        return dto;
    }
}
