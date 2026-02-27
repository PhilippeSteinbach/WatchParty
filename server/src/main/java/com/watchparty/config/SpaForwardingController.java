package com.watchparty.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards non-API, non-static-asset requests to index.html so that
 * Angular's client-side router can handle them.
 * Only active when the Angular frontend is embedded in the Spring Boot JAR
 * (standalone / single-container deployment).
 *
 * The regex {@code [^\\.]+} matches path segments without dots, which
 * excludes static files (e.g. main.js, styles.css, favicon.ico).
 * The root path {@code /} is served by Spring's WelcomePageHandlerMapping.
 */
@Controller
@ConditionalOnResource(resources = "classpath:/static/index.html")
public class SpaForwardingController {

    @GetMapping("/{path:[^\\.]+}")
    public String forwardSingle() {
        return "forward:/index.html";
    }

    @GetMapping("/{path1:[^\\.]+}/{path2:[^\\.]+}")
    public String forwardNested() {
        return "forward:/index.html";
    }

    @GetMapping("/{path1:[^\\.]+}/{path2:[^\\.]+}/{path3:[^\\.]+}")
    public String forwardDeepNested() {
        return "forward:/index.html";
    }
}
