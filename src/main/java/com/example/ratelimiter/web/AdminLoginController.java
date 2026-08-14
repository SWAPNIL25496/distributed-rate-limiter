package com.example.ratelimiter.web;

import com.example.ratelimiter.config.AppProperties;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/drl/admin")
public class AdminLoginController {

    private final AppProperties appProperties;

    public AdminLoginController(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @GetMapping("/login")
    public String loginForm(
            @RequestParam(value = "error", required = false) String error, Model model) {
        model.addAttribute("error", error != null);
        return "admin/login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam("apiKey") String apiKey, HttpSession session) {
        if (apiKey == null || !isValid(apiKey)) {
            return "redirect:/drl/admin/login?error";
        }
        session.setAttribute(AdminSession.API_KEY_ATTRIBUTE, apiKey);
        return "redirect:/drl/admin";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/drl/admin/login";
    }

    private boolean isValid(String provided) {
        byte[] expected = appProperties.apiKey().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8));
    }
}
