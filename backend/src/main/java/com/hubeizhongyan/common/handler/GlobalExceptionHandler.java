/*
 * 文件名称：GlobalExceptionHandler
 * 文件说明：全局异常处理器，统一收敛控制层和业务层抛出的异常，向前端返回结构化响应，
 *          同时对正式 PostgreSQL 数据库未连通、未建库或未完成初始化的场景提供明确提示，
 *          便于前后端联调和生产问题定位。
 * 主要职责：
 * 1. 统一处理业务异常并透传业务提示信息。
 * 2. 统一处理参数校验异常，避免前端收到不明确的错误返回。
 * 3. 统一处理数据库连接与库表初始化异常，给出正式库接入排查指引。
 * 开发者：czd
 */
package com.hubeizhongyan.common.handler;

import com.hubeizhongyan.common.domain.ApiResponse;
import com.hubeizhongyan.common.exception.BusinessException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常直接按业务编码返回，保证前端可以拿到可读提示。
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException ex) {
        return ApiResponse.fail(ex.getCode(), ex.getMessage());
    }

    /**
     * 参数绑定和 JSON 解析异常统一返回 400，避免把客户端问题误判为服务端问题。
     */
    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        BindException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class
    })
    public ApiResponse<Void> handleBadRequest(Exception ex) {
        return ApiResponse.fail(400, "请求参数不合法");
    }

    /**
     * PostgreSQL 无法建立连接时给出明确引导，便于快速定位为建库、账号或密码问题。
     */
    @ExceptionHandler(CannotGetJdbcConnectionException.class)
    public ApiResponse<Void> handleJdbcConnectionException(CannotGetJdbcConnectionException ex) {
        return ApiResponse.fail(500, "无法连接 PostgreSQL，请确认 hbzy 数据库已创建且账号密码配置正确");
    }

    /**
     * 正式表未初始化时返回清晰提示，避免前端只能看到笼统的系统异常。
     */
    @ExceptionHandler(BadSqlGrammarException.class)
    public ApiResponse<Void> handleBadSqlGrammarException(BadSqlGrammarException ex) {
        return ApiResponse.fail(500, "正式库表未初始化完成，请先执行 scripts/init-postgresql.ps1 导入 hbzy.sql 数据");
    }

    /**
     * 兜底异常处理，避免未识别异常直接将堆栈暴露给前端。
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception ex) {
        return ApiResponse.fail(500, "系统内部错误");
    }
}
