package de.atiw.volleyball.teacher.common

import de.atiw.volleyball.dto.ApiErrorBody
import de.atiw.volleyball.dto.ErrorEnvelope
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException): ResponseEntity<ErrorEnvelope> =
        ResponseEntity.status(ex.status)
            .body(ErrorEnvelope(ApiErrorBody(ex.code, ex.message)))

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleConflict(ex: DataIntegrityViolationException): ResponseEntity<ErrorEnvelope> =
        ResponseEntity.status(409)
            .body(ErrorEnvelope(ApiErrorBody("CONFLICT", "Request conflicts with existing data.")))

    @ExceptionHandler(MethodArgumentNotValidException::class, HttpMessageNotReadableException::class)
    fun handleBadRequest(ex: Exception): ResponseEntity<ErrorEnvelope> =
        ResponseEntity.status(400)
            .body(ErrorEnvelope(ApiErrorBody("BAD_REQUEST", "Invalid request data.")))

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorEnvelope> =
        ResponseEntity.status(500)
            .body(ErrorEnvelope(ApiErrorBody("INTERNAL_ERROR", "Unexpected server error.")))
}
