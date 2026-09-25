package de.atiw.volleyball.admin.common

import org.springframework.http.HttpStatus

open class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String
) : RuntimeException(message)

class BadRequestException(message: String) :
    ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message)

class NotFoundException(entity: String) :
    ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "$entity not found")

class ConflictException(message: String) :
    ApiException(HttpStatus.CONFLICT, "CONFLICT", message)
