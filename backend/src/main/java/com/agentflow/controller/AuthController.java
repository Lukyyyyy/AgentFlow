package com.agentflow.controller;

import com.agentflow.common.BizException;
import com.agentflow.common.Result;
import com.agentflow.dto.LoginRequest;
import com.agentflow.dto.LoginResponse;
import com.agentflow.dto.RefreshTokenRequest;
import com.agentflow.dto.RegisterRequest;
import com.agentflow.dto.RegistrationCodeRequest;
import com.agentflow.service.AuthService;
import com.agentflow.service.EmailVerificationService;
import com.agentflow.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Tag(name = "认证接口")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private AuthService authService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private RegistrationService registrationService;

    @Operation(summary = "用户登录（仅支持邮箱）")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthTokens tokens = authService.login(request.getEmail(), request.getPassword());
        if (tokens != null) {
            LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(tokens.username(), tokens.email());
            LoginResponse response = new LoginResponse(tokens.accessToken(), tokens.refreshToken(), userInfo);
            return Result.success(response);
        }
        return Result.error("邮箱或密码错误");
    }

    @Operation(summary = "发送注册验证码")
    @PostMapping("/registration-code")
    public Result<Void> sendRegistrationCode(@Valid @RequestBody RegistrationCodeRequest request) {
        try {
            emailVerificationService.sendRegistrationCode(request.getEmail());
            return Result.success();
        } catch (BizException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }

    @Operation(summary = "邮箱注册（成功后自动登录）")
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        try {
            String username = registrationService.register(
                    request.getEmail(), request.getVerificationCode(), request.getPassword());
            AuthService.AuthTokens tokens = authService.issueTokens(username);
            LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(username, tokens.email());
            return Result.success(new LoginResponse(tokens.accessToken(), tokens.refreshToken(), userInfo));
        } catch (BizException e) {
            return Result.error(e.getCode(), e.getMessage());
        }
    }
    
    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(request != null ? request.getRefreshToken() : null);
        return Result.success();
    }

    @Operation(summary = "刷新访问令牌")
    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            return Result.unauthorized("Refresh Token 不能为空");
        }

        AuthService.AuthTokens tokens = authService.refresh(request.getRefreshToken());
        if (tokens == null) {
            return Result.unauthorized("Refresh Token 无效或已过期");
        }

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo(tokens.username(), tokens.email());
        return Result.success(new LoginResponse(tokens.accessToken(), tokens.refreshToken(), userInfo));
    }
    
    @Operation(summary = "获取当前用户信息")
    @GetMapping("/current")
    public Result<LoginResponse.UserInfo> getCurrentUser(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            String username = authService.getUsernameByToken(token);
            if (username != null) {
                return Result.success(new LoginResponse.UserInfo(username, authService.getEmailByUsername(username)));
            }
        }
        return Result.unauthorized("未认证");
    }
}
