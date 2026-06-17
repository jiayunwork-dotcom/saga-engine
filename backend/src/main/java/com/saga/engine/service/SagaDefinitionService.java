package com.saga.engine.service;

import com.saga.engine.dto.CreateSagaDefinitionRequest;
import com.saga.engine.dto.SagaDefinitionDTO;
import com.saga.engine.dto.UpdateSagaDefinitionRequest;
import com.saga.engine.entity.SagaDefinition;
import com.saga.engine.repository.SagaDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaDefinitionService {

    private final SagaDefinitionRepository sagaDefinitionRepository;

    @Transactional
    public SagaDefinitionDTO createDefinition(CreateSagaDefinitionRequest request, String username) {
        Integer currentMaxVersion = sagaDefinitionRepository.findMaxVersionByName(request.getName());
        
        SagaDefinition definition = new SagaDefinition();
        definition.setName(request.getName());
        definition.setDescription(request.getDescription());
        definition.setVersion(currentMaxVersion != null ? currentMaxVersion + 1 : 1);
        definition.setDefinition(request.getDefinition());
        definition.setCreatedBy(username);

        SagaDefinition saved = sagaDefinitionRepository.save(definition);
        log.info("Created saga definition: {} version {}", saved.getName(), saved.getVersion());
        
        return convertToDTO(saved);
    }

    @Transactional
    public SagaDefinitionDTO updateDefinition(Long id, UpdateSagaDefinitionRequest request, String username) {
        SagaDefinition existing = sagaDefinitionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Saga definition not found: " + id));

        Integer maxVersion = sagaDefinitionRepository.findMaxVersionByName(existing.getName());
        
        SagaDefinition newDefinition = new SagaDefinition();
        newDefinition.setName(existing.getName());
        newDefinition.setDescription(request.getDescription());
        newDefinition.setVersion(maxVersion + 1);
        newDefinition.setDefinition(request.getDefinition());
        newDefinition.setCreatedBy(username);

        SagaDefinition saved = sagaDefinitionRepository.save(newDefinition);
        log.info("Updated saga definition: {} new version {}", saved.getName(), saved.getVersion());
        
        return convertToDTO(saved);
    }

    public SagaDefinitionDTO getDefinition(Long id) {
        SagaDefinition definition = sagaDefinitionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Saga definition not found: " + id));
        return convertToDTO(definition);
    }

    public SagaDefinitionDTO getLatestDefinition(String name) {
        List<SagaDefinition> definitions = sagaDefinitionRepository.findByNameOrderByVersionDesc(name);
        if (definitions.isEmpty()) {
            throw new RuntimeException("Saga definition not found: " + name);
        }
        return convertToDTO(definitions.get(0));
    }

    public SagaDefinition getLatestDefinitionEntity(String name) {
        List<SagaDefinition> definitions = sagaDefinitionRepository.findByNameOrderByVersionDesc(name);
        if (definitions.isEmpty()) {
            throw new RuntimeException("Saga definition not found: " + name);
        }
        return definitions.get(0);
    }

    public SagaDefinition getDefinitionEntityByNameAndVersion(String name, Integer version) {
        return sagaDefinitionRepository.findByNameAndVersion(name, version)
                .orElseThrow(() -> new RuntimeException("Saga definition not found: " + name + " version " + version));
    }

    public List<SagaDefinitionDTO> getAllDefinitions() {
        List<String> names = sagaDefinitionRepository.findAllDistinctNames();
        List<SagaDefinitionDTO> result = new ArrayList<>();
        for (String name : names) {
            List<SagaDefinition> definitions = sagaDefinitionRepository.findByNameOrderByVersionDesc(name);
            if (!definitions.isEmpty()) {
                result.add(convertToDTO(definitions.get(0)));
            }
        }
        return result;
    }

    public List<SagaDefinitionDTO> getDefinitionVersions(String name) {
        List<SagaDefinition> definitions = sagaDefinitionRepository.findByNameOrderByVersionDesc(name);
        return definitions.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<SagaDefinitionDTO> searchDefinitions(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return getAllDefinitions();
        }
        List<SagaDefinition> definitions = sagaDefinitionRepository.searchByName(keyword);
        Map<String, SagaDefinition> latestMap = new HashMap<>();
        for (SagaDefinition def : definitions) {
            if (!latestMap.containsKey(def.getName()) || 
                def.getVersion() > latestMap.get(def.getName()).getVersion()) {
                latestMap.put(def.getName(), def);
            }
        }
        return latestMap.values().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteDefinition(Long id) {
        sagaDefinitionRepository.deleteById(id);
        log.info("Deleted saga definition: {}", id);
    }

    private SagaDefinitionDTO convertToDTO(SagaDefinition entity) {
        SagaDefinitionDTO dto = new SagaDefinitionDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setVersion(entity.getVersion());
        dto.setDefinition(entity.getDefinition());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
