package com.xhs.audit.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 页面Controller
 * 提供前端页面路由
 *
 * @author XHS Audit System
 * @since 2026-01-29
 */
@Controller
public class PageController {

    /**
     * 首页 - 运营人员审核系统主页面
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }
}
