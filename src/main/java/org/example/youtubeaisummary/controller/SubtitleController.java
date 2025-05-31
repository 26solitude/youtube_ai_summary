package org.example.youtubeaisummary.controller;

import org.example.youtubeaisummary.service.SubtitleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SubtitleController {

    private final SubtitleService subtitleService;

    public SubtitleController(SubtitleService subtitleService) {
        this.subtitleService = subtitleService;
    }

    @GetMapping(value = "/subs")
    public String getSubtitle(@RequestParam String url) {
        return subtitleService.fetchSubs(url);
    }
}