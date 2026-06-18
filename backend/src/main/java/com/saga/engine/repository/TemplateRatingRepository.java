package com.saga.engine.repository;

import com.saga.engine.entity.TemplateRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateRatingRepository extends JpaRepository<TemplateRating, Long> {

    Optional<TemplateRating> findByTemplateIdAndUserId(Long templateId, Long userId);

    List<TemplateRating> findByTemplateId(Long templateId);

    @org.springframework.data.jpa.repository.Query("SELECT AVG(r.score) FROM TemplateRating r WHERE r.templateId = :templateId")
    Double getAverageScoreByTemplateId(@org.springframework.data.repository.query.Param("templateId") Long templateId);

    long countByTemplateId(Long templateId);
}
