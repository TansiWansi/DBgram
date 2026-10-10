package com.dbgram.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Exact exported page routes keep API and management paths out of frontend
 * fallbacks.
 */
@Controller
public class FrontendController {
    public static final String[] PUBLIC_GET_PATHS = {
            "/", "/login", "/register", "/feed", "/reels", "/saved", "/create", "/explore", "/profile",
            "/post", "/history", "/privacy", "/communities", "/community", "/messages", "/notifications", "/analytics",
            "/report", "/settings", "/stories",
            "/index.html", "/login.html", "/register.html", "/feed.html", "/reels.html", "/saved.html", "/create.html",
            "/explore.html", "/profile.html", "/post.html", "/history.html", "/privacy.html", "/communities.html",
            "/community.html", "/messages.html", "/notifications.html", "/analytics.html", "/report.html", "/settings.html", "/stories.html",
            "/_expo/**", "/assets/**", "/favicon.ico"
    };

    @GetMapping({ "/", "/login", "/register", "/feed", "/reels", "/saved", "/create", "/explore", "/profile",
            "/post", "/history", "/privacy", "/communities", "/community", "/messages", "/notifications", "/analytics",
            "/report", "/settings", "/stories" })
    public String page(HttpServletRequest request) {
        String path = request.getServletPath();
        return "forward:" + ((path.equals("/") || path.equals("/feed")) ? "/index.html" : path + ".html");
    }
}
