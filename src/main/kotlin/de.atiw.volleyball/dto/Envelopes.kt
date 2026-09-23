package de.atiw.volleyball.dto

data class DataEnvelope<T>(val data: T)

data class ApiErrorBody(
    val code: String,
    val message: String,
    val details: Map<String, Any> = emptyMap()
)

data class ErrorEnvelope(val error: ApiErrorBody)
