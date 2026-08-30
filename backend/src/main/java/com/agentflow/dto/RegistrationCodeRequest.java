package com.agentflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送注册验证码请求 DTO
 */
@Data
public class RegistrationCodeRequest {

    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式不正确")
    private String email;
}
