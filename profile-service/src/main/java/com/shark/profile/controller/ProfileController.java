package com.shark.profile.controller;

import com.shark.profile.dto.GameHistoryResponse;
import com.shark.profile.dto.ProfileResponse;
import com.shark.profile.dto.UpdateColorRequest;
import com.shark.profile.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    private UUID getCurrentUserId(HttpServletRequest request) {
        return (UUID) request.getAttribute("currentUserId");
    }

    @GetMapping("/me")
    public ResponseEntity<ProfileResponse> getMyProfile(HttpServletRequest request) {
        UUID userId = getCurrentUserId(request);
        return ResponseEntity.ok(profileService.getProfileByUserId(userId));
    }

    @PutMapping("/color")
    public ResponseEntity<ProfileResponse> updateColor(@Valid @RequestBody UpdateColorRequest colorRequest, 
                                                       HttpServletRequest request) {
        UUID userId = getCurrentUserId(request);
        profileService.updateSharkColor(userId, colorRequest.colorHex());
        return ResponseEntity.ok(profileService.getProfileByUserId(userId));
    }

    @GetMapping("/me/history")
    public ResponseEntity<List<GameHistoryResponse>> getMyHistory(HttpServletRequest request) {
        UUID userId = getCurrentUserId(request);
        return ResponseEntity.ok(profileService.getHistory(userId));
    }
}
