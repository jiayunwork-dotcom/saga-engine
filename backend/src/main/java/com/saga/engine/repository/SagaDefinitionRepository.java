package com.saga.engine.repository;

import com.saga.engine.entity.SagaDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SagaDefinitionRepository extends JpaRepository<SagaDefinition, Long> {

    List<SagaDefinition> findByNameOrderByVersionDesc(String name);

    Optional<SagaDefinition> findByNameAndVersion(String name, Integer version);

    @Query("SELECT MAX(s.version) FROM SagaDefinition s WHERE s.name = :name")
    Integer findMaxVersionByName(@Param("name") String name);

    @Query("SELECT s FROM SagaDefinition s WHERE s.name LIKE %:keyword% ORDER BY s.name, s.version DESC")
    List<SagaDefinition> searchByName(@Param("keyword") String keyword);

    @Query("SELECT DISTINCT s.name FROM SagaDefinition s ORDER BY s.name")
    List<String> findAllDistinctNames();

    @Query("SELECT DISTINCT s.name FROM SagaDefinition s WHERE s.createdBy = :createdBy ORDER BY s.name")
    List<String> findDistinctNamesByCreatedBy(@Param("createdBy") String createdBy);
}
