package com.watchparty.controller;

import com.watchparty.dto.VideoRecommendation;
import com.watchparty.service.YouTubeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @GetMapping("/playlist/{playlistId}")
    public ResponseEntity<?> getPlaylistInfo(@PathVariable String playlistId) {
        Optional<YouTubeService.PlaylistInfo> info = youTubeService.fetchPlaylistItems(playlistId);
        if (info.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Could not fetch playlist. YouTube API key may not be configured."));
        }
        return ResponseEntity.ok(info.get());
    }
}
