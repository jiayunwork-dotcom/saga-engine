package com.saga.engine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReviewTemplateRequest {

    @NotNull(message = "模板ID不能为空")
    private Long templateId;

    @NotBlank(message = "审核结果不能为空")
    @jakarta.validation.constraints.Pattern(regexp = "APPROVED|REJECTED|REVISION_REQUIRED", message = "审核结果必须为APPROVED、REJECTED或REVISION_REQUIRED")
    private String result;

    @Size(min = 10, message = "退回修改意见不少于10个字")
    private String comment;
}
