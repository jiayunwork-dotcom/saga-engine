package com.saga.engine.service;

import com.saga.engine.dto.*;
import com.saga.engine.entity.SagaDefinition;
import com.saga.engine.entity.SagaTemplate;
import com.saga.engine.entity.TemplateRating;
import com.saga.engine.entity.User;
import com.saga.engine.enums.TemplateStatus;
import com.saga.engine.repository.SagaDefinitionRepository;
import com.saga.engine.repository.SagaTemplateRepository;
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

        SagaTemplate template = new SagaTemplate();
        template.setName(request.getName());
        template.setDescription(request.getDescription());
        template.setCategoryTags(request.getCategoryTags());
        template.setVersion(request.getVersion());
        template.setStepDefinition(request.getStepDefinition());
        template.setPublisher(username);
        template.setSceneDescription(request.getSceneDescription());
        template.setStatus(TemplateStatus.PENDING_REVIEW);
        template.setDownloadCount(0);

        SagaTemplate saved = sagaTemplateRepository.save(template);
        log.info("Template published: {} version {}, by {}", saved.getName(), saved.getVersion(), username);
        return convertToDTO(saved);
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
        } else {
            template.setStatus(TemplateStatus.REJECTED);
        }
        template.setReviewer(reviewer);
        template.setReviewedAt(LocalDateTime.now());

        SagaTemplate saved = sagaTemplateRepository.save(template);
        log.info("Template {} version {} reviewed by {}: {}", saved.getName(), saved.getVersion(), reviewer, request.getResult());
        return convertToDTO(saved);
    }

    public Page<TemplateDTO> searchTemplates(String keyword, String category, int page, int size, String sortBy) {
        Sort sort;
        if ("downloadCount".equals(sortBy)) {
            sort = Sort.by(Sort.Direction.DESC, "downloadCount");
        } else {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<SagaTemplate> templatePage;
        if ((category != null && !category.isEmpty()) && (keyword != null && !keyword.isEmpty())) {
            templatePage = sagaTemplateRepository.searchByKeywordAndCategoryAndStatus(keyword, category, TemplateStatus.PUBLISHED.name(), pageable);
        } else if (keyword != null && !keyword.isEmpty()) {
            templatePage = sagaTemplateRepository.searchByKeywordAndStatus(keyword, TemplateStatus.PUBLISHED, pageable);
        } else {
            templatePage = sagaTemplateRepository.findByStatus(TemplateStatus.PUBLISHED, pageable);
        }

        return templatePage.map(this::convertToDTO);
    }

    public TemplateDTO getTemplateDetail(Long id) {
        SagaTemplate template = sagaTemplateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("模板不存在: " + id));
        return convertToDTO(template);
    }

    public List<TemplateDTO> getTemplateVersions(String name) {
        List<SagaTemplate> templates = sagaTemplateRepository.findByNameOrderByCreatedAtDesc(name);
        return templates.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public SagaDefinitionDTO importTemplate(ImportTemplateRequest request, String username) {
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
        return dto;
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

    private TemplateDTO convertToDTO(SagaTemplate entity) {
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
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setReviewedAt(entity.getReviewedAt());

        Double avgScore = templateRatingRepository.getAverageScoreByTemplateId(entity.getId());
        dto.setAverageScore(avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0.0);
        dto.setRatingCount(templateRatingRepository.countByTemplateId(entity.getId()));

        return dto;
    }
}
