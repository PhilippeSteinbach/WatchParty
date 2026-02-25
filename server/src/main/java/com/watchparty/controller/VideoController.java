package com.watchparty.controller;

import com.watchparty.dto.VideoRecommendation;
import com.watchparty.service.YouTubeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos")
public class VideoController {

    private final YouTubeService youTubeService;

    public VideoController(YouTubeService youTubeService) {
        this.youTubeService = youTubeService;
    }

    @GetMapping("/{videoId}/recommendations")
    public List<VideoRecommendation> getRecommendations(
            @PathVariable String videoId,
            @RequestParam(defaultValue = "6") int limit) {
        return youTubeService.searchRelated(videoId, Math.min(limit, 12));
    }
}
