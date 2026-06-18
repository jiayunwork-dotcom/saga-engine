package com.saga.engine.repository;

import com.saga.engine.entity.SagaTemplate;
import com.saga.engine.enums.TemplateStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SagaTemplateRepository extends JpaRepository<SagaTemplate, Long> {

    List<SagaTemplate> findByNameOrderByCreatedAtDesc(String name);

    Optional<SagaTemplate> findByNameAndVersion(String name, String version);

    Page<SagaTemplate> findByStatus(TemplateStatus status, Pageable pageable);

    @Query("SELECT t FROM SagaTemplate t WHERE t.status = :status AND " +
           "(:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<SagaTemplate> searchByKeywordAndStatus(@Param("keyword") String keyword,
                                                 @Param("status") TemplateStatus status,
                                                 Pageable pageable);

    @Query(value = "SELECT * FROM saga_template t WHERE t.status = :status " +
           "AND (:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:category IS NULL OR t.category_tags::text LIKE CONCAT('%', :category, '%'))",
           nativeQuery = true)
    Page<SagaTemplate> searchByKeywordAndCategoryAndStatus(@Param("keyword") String keyword,
                                                            @Param("category") String category,
                                                            @Param("status") String status,
                                                            Pageable pageable);

    @Query("SELECT t FROM SagaTemplate t WHERE t.name = :name AND t.status = :status ORDER BY t.createdAt DESC")
    List<SagaTemplate> findByNameAndStatus(@Param("name") String name, @Param("status") TemplateStatus status);

    @Query(value = "SELECT t.* FROM saga_template t " +
           "LEFT JOIN template_rating r ON t.id = r.template_id " +
           "WHERE t.status = :status " +
           "GROUP BY t.id " +
           "ORDER BY AVG(r.score) DESC NULLS LAST, t.download_count DESC",
           nativeQuery = true)
    Page<SagaTemplate> findByStatusOrderByRating(@Param("status") TemplateStatus status, Pageable pageable);

    @Query(value = "SELECT t.* FROM saga_template t " +
           "LEFT JOIN template_rating r ON t.id = r.template_id " +
           "WHERE t.status = :status " +
           "AND (:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "GROUP BY t.id " +
           "ORDER BY AVG(r.score) DESC NULLS LAST, t.download_count DESC",
           nativeQuery = true)
    Page<SagaTemplate> searchByKeywordAndStatusOrderByRating(@Param("keyword") String keyword,
                                                              @Param("status") TemplateStatus status,
                                                              Pageable pageable);

    @Query(value = "SELECT t.* FROM saga_template t " +
           "LEFT JOIN template_rating r ON t.id = r.template_id " +
           "WHERE t.status = :status " +
           "AND (:keyword IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(t.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:category IS NULL OR t.category_tags::text LIKE CONCAT('%', :category, '%')) " +
           "GROUP BY t.id " +
           "ORDER BY AVG(r.score) DESC NULLS LAST, t.download_count DESC",
           nativeQuery = true)
    Page<SagaTemplate> searchByKeywordAndCategoryAndStatusOrderByRating(@Param("keyword") String keyword,
                                                                         @Param("category") String category,
                                                                         @Param("status") String status,
                                                                         Pageable pageable);
}
