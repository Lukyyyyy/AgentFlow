package com.agentflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 邮箱注册请求 DTO
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "请输入验证码")
    @Pattern(regexp = "\\d{6}", message = "验证码格式不正确")
    private String verificationCode;

    @NotBlank(message = "请输入密码")
    private String password;
}
