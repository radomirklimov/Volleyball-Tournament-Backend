package de.atiw.volleyball.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Enforces port-based API separation in the single application:
 *
 * - public port: only the public read API (`/`, `/api/groups…`, `/api/teams…`,
 *   `/api/rounds…`, `/api/fields…`, `GET /api/games…`) and the public
 *   WebSocket handshake (`/ws/live`). Admin routes get `404`.
 * - admin port: only the admin API (`/api/admin/…` and the game-start
 *   `POST /api/games/{id}/start` endpoint). Public read routes and
 *   the public WebSocket handshake get `404`.
 *
 * `GET /` (redirect to the Swagger UI) is allowed on both ports: each port
 * serves its own API docs, so the landing page must work everywhere.
 *
 * Requests arriving on an unknown port (e.g. MockMvc tests, which use a
 * synthetic local port) are passed through so existing MockMvc-based tests
 * keep working; real port isolation is covered by dedicated integration
 * tests using real HTTP against both ports.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class PortIsolationFilter(
    private val ports: PortRegistry
) : OncePerRequestFilter() {

    private val scoringPath = Regex("^/api/games/[^/]+/(start|end)$")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val publicPort = ports.publicPort
        val adminPort = ports.adminPort
        if (publicPort != null && adminPort != null) {
            val path = request.requestURI.removePrefix(request.contextPath)
            val blocked = when (request.localPort) {
                publicPort -> isAdminRoute(request.method, path)
                adminPort -> isPublicRoute(request.method, path)
                else -> false
            }
            if (blocked) {
                response.status = HttpServletResponse.SC_NOT_FOUND
                response.contentType = "application/json"
                response.writer.write(
                    """{"error":{"code":"RESOURCE_NOT_FOUND","message":"Not found"}}"""
                )
                return
            }
        }
        chain.doFilter(request, response)
    }

    private fun isAdminRoute(method: String, path: String): Boolean {
        if (path == "/api/admin" || path.startsWith("/api/admin/")) return true
        // The game-start URL stays under /api/games/… but is admin-only.
        if (method == "POST" && scoringPath.matches(path)) return true
        return false
    }

    private fun isPublicRoute(method: String, path: String): Boolean {
        if (path == "/ws/live" || path.startsWith("/ws/live/")) return true
        if (method == "GET") {
            // NB: "/" is intentionally NOT listed here — the Swagger UI
            // redirect (RootController) must work on both ports.
            if (path == "/api/groups" || path.startsWith("/api/groups/")) return true
            if (path == "/api/teams" || path.startsWith("/api/teams/")) return true
            if (path == "/api/rounds" || path.startsWith("/api/rounds/")) return true
            if (path == "/api/fields" || path.startsWith("/api/fields/")) return true
            if (path == "/api/games" || path.startsWith("/api/games/")) return true
        }
        return false
    }
}
