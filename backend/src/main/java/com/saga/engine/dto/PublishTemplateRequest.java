package com.saga.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PublishTemplateRequest {

    @NotBlank(message = "模板名称不能为空")
    private String name;

    private String description;

    @Size(max = 5, message = "分类标签最多5个")
    private List<String> categoryTags;

    @NotBlank(message = "版本号不能为空")
    @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "版本号格式必须为x.y.z")
    private String version;

    @NotNull(message = "步骤定义不能为空")
    private List<Map<String, Object>> stepDefinition;

    private String sceneDescription;
}
