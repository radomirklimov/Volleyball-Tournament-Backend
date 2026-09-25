package de.atiw.volleyball.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper

/**
 * Scopes Swagger/OpenAPI discovery per port so each port's UI only shows its
 * own API surface (mirroring [PortIsolationFilter]):
 *
 * - public port serves `/v3/api-docs/public…`, rejects `/v3/api-docs/admin…`
 *   and the aggregate `/v3/api-docs`
 * - admin port serves `/v3/api-docs/admin…`, rejects `/v3/api-docs/public…`
 *   and the aggregate `/v3/api-docs`
 * - `/v3/api-docs/swagger-config` (which feeds the UI's group dropdown) is
 *   rewritten per port to list only that port's group
 *
 * The Swagger UI static resources stay available on both ports;
 * they only ever display the group allowed on that port.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class SwaggerPortFilter(
    private val ports: PortRegistry,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    companion object {
        const val SWAGGER_CONFIG_PATH = "/v3/api-docs/swagger-config"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val publicPort = ports.publicPort
        val adminPort = ports.adminPort
        if (publicPort == null || adminPort == null) {
            chain.doFilter(request, response)
            return
        }
        val group = when (request.localPort) {
            publicPort -> "public"
            adminPort -> "admin"
            else -> null
        }
        if (group == null) {
            chain.doFilter(request, response)
            return
        }
        val path = request.requestURI.removePrefix(request.contextPath)
        if (!isApiDocs(path)) {
            chain.doFilter(request, response)
            return
        }
        if (path == SWAGGER_CONFIG_PATH) {
            val wrapper = ContentCachingResponseWrapper(response)
            chain.doFilter(request, wrapper)
            rewriteSwaggerConfig(wrapper, group)
            return
        }
        if (isGroupDocs(path, group)) {
            chain.doFilter(request, response)
            return
        }
        response.status = HttpServletResponse.SC_NOT_FOUND
        response.contentType = "application/json"
        response.writer.write(
            """{"error":{"code":"RESOURCE_NOT_FOUND","message":"Not found"}}"""
        )
    }

    private fun isApiDocs(path: String): Boolean =
        path == "/v3/api-docs" || path.startsWith("/v3/api-docs/") ||
            path == "/v3/api-docs.yaml" || path.startsWith("/v3/api-docs.yaml")

    private fun isGroupDocs(path: String, group: String): Boolean {
        if (path == "/v3/api-docs/$group" || path.startsWith("/v3/api-docs/$group/")) return true
        if (path == "/v3/api-docs/$group.json" || path == "/v3/api-docs/$group.yaml") return true
        return false
    }

    private fun rewriteSwaggerConfig(wrapper: ContentCachingResponseWrapper, group: String) {
        val original = wrapper.contentAsByteArray
        val modified: ByteArray = try {
            val root = objectMapper.readTree(original)
            val urls = root.path("urls")
            if (root is ObjectNode && urls.isArray) {
                val kept = objectMapper.createArrayNode()
                for (entry in urls) {
                    if (entry.path("name").asText() == group) kept.add(entry)
                }
                root.replace("urls", kept)
                objectMapper.writeValueAsBytes(root)
            } else {
                original
            }
        } catch (_: Exception) {
            original
        }
        val real = wrapper.response as HttpServletResponse
        real.contentType = wrapper.contentType ?: "application/json"
        real.characterEncoding = wrapper.characterEncoding
        real.setContentLength(modified.size)
        real.outputStream.write(modified)
    }
}
