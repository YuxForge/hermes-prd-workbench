package org.hermesprd.workbench.shared;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final org.hermesprd.workbench.telemetry.TelemetryService telemetryService;

    public ApiExceptionHandler(org.hermesprd.workbench.telemetry.TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> handleApi(ApiException ex) {
        telemetryService.recordSystemError("API", "API_EXCEPTION", ex.code(), ex.getMessage());
        return body(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("请求无效");
        telemetryService.recordSystemError("API", "VALIDATION_ERROR", "INVALID_REQUEST", message);
        return body(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleTooLarge(MaxUploadSizeExceededException ex) {
        telemetryService.recordSystemError("SOURCE", "UPLOAD_TOO_LARGE", "FILE_TOO_LARGE", "文件超过 Spike 限制");
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "文件超过 Spike 限制");
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    public ResponseEntity<Map<String, String>> handleInvalidMultipart(Exception ex) {
        telemetryService.recordSystemError("SOURCE", "INVALID_MULTIPART", "INVALID_REQUEST", "上传请求格式无效");
        return body(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "上传请求格式无效");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnknown(Exception ex, HttpServletRequest request) {
        String requestId = currentRequestId();
        log.warn("Unhandled error requestId={} type={}", requestId, ex.getClass().getSimpleName(), ex);
        telemetryService.recordSystemError("SYSTEM", "API_REQUEST", "INTERNAL_ERROR", "服务内部错误");
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务暂时无法处理该请求");
    }

    private ResponseEntity<Map<String, String>> body(HttpStatus status, String code, String message) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        payload.put("requestId", currentRequestId());
        return ResponseEntity.status(status).body(payload);
    }

    private String currentRequestId() {
        String requestId = MDC.get("requestId");
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString().replace("-", "") : requestId;
    }
}
