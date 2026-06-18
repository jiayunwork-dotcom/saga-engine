package com.saga.engine.repository;

import com.saga.engine.entity.TemplateFavorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateFavoriteRepository extends JpaRepository<TemplateFavorite, Long> {

    Optional<TemplateFavorite> findByTemplateIdAndUserId(Long templateId, Long userId);

    List<TemplateFavorite> findByUserId(Long userId);

    boolean existsByTemplateIdAndUserId(Long templateId, Long userId);

    void deleteByTemplateIdAndUserId(Long templateId, Long userId);

    @Query("SELECT t FROM SagaTemplate t JOIN TemplateFavorite f ON t.id = f.templateId " +
           "WHERE f.userId = :userId AND t.status = 'PUBLISHED' " +
           "AND (:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<com.saga.engine.entity.SagaTemplate> findFavoriteTemplatesByUserId(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query(value = "SELECT t.* FROM saga_template t " +
           "JOIN template_favorite f ON t.id = f.template_id " +
           "WHERE f.user_id = :userId AND t.status = 'PUBLISHED' " +
           "AND (:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:category IS NULL OR t.category_tags::text LIKE CONCAT('%', :category, '%'))",
           nativeQuery = true)
    Page<com.saga.engine.entity.SagaTemplate> findFavoriteTemplatesByUserIdAndCategory(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("category") String category,
            Pageable pageable);

    @Query(value = "SELECT t.* FROM saga_template t " +
           "JOIN template_favorite f ON t.id = f.template_id " +
           "LEFT JOIN template_rating r ON t.id = r.template_id " +
           "WHERE f.user_id = :userId AND t.status = 'PUBLISHED' " +
           "AND (:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:category IS NULL OR t.category_tags::text LIKE CONCAT('%', :category, '%')) " +
           "GROUP BY t.id " +
           "ORDER BY AVG(r.score) DESC NULLS LAST, t.download_count DESC",
           nativeQuery = true)
    Page<com.saga.engine.entity.SagaTemplate> findFavoriteTemplatesByUserIdOrderByRating(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("category") String category,
            Pageable pageable);
}
