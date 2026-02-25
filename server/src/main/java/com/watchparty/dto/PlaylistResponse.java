package com.watchparty.dto;

import java.util.List;

public record PlaylistResponse(
        List<PlaylistItemResponse> items
) {
}
