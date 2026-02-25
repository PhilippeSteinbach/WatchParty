package com.watchparty.security;

import java.util.UUID;

public record AuthenticatedUser(UUID userId, String email) {}
