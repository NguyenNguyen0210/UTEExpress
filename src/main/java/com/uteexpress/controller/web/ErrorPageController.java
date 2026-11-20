package com.uteexpress.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/error")
public class ErrorPageController {

    @GetMapping("/403")
    public String forbiddenPage() {
        return "error/403";
    }

    @GetMapping("/404")
    public String notFoundPage() {
        return "error/404";
    }
}
