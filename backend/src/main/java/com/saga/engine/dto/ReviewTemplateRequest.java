package com.saga.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewTemplateRequest {

    @NotNull(message = "模板ID不能为空")
    private Long templateId;

    @NotBlank(message = "审核结果不能为空")
    @jakarta.validation.constraints.Pattern(regexp = "APPROVED|REJECTED", message = "审核结果必须为APPROVED或REJECTED")
    private String result;
}
