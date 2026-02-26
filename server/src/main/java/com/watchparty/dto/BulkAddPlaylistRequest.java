package com.watchparty.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record BulkAddPlaylistRequest(@NotEmpty List<String> videoUrls) {}
