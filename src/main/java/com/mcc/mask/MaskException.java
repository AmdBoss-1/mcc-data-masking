package com.mcc.mask;

/** 脱敏工具统一业务异常（CLI 层映射为退出码 2）。 */
public class MaskException extends RuntimeException {
    public MaskException(String message) {
        super(message);
    }

    public MaskException(String message, Throwable cause) {
        super(message, cause);
    }
}
