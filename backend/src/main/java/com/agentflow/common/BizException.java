package com.agentflow.common;

import lombok.Getter;

/**
 * 业务异常（携带响应码，由控制器统一转换为 Result 返回）
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(String message) {
        this(Result.ERROR_CODE, message);
    }
}
