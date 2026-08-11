package com.pulsetrack.backend.admin.dto;

import com.pulsetrack.backend.user.Role;

import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(@NotNull Role role) {
}
