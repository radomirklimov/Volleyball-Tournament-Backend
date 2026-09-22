package de.atiw.volleyball.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.servlet.view.RedirectView

@Controller
class RootController {
    @GetMapping
    fun redirectRootToSwaggerUi(): RedirectView = RedirectView(SWAGGER_UI)

    companion object {
        private const val SWAGGER_UI = "/swagger-ui/index.html"
    }
}
