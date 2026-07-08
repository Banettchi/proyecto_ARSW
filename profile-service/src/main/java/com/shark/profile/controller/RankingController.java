package com.shark.profile.controller;

import com.shark.profile.dto.ProfileResponse;
import com.shark.profile.dto.RankingEntryResponse;
import com.shark.profile.dto.RankingPageResponse;
import com.shark.profile.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/rankings")
public class RankingController {

    private final ProfileService profileService;

    public RankingController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<RankingPageResponse> getGlobalRanking(
            @RequestParam(defaultValue = "50") int limit,
            HttpServletRequest request) {
        
        UUID userId = (UUID) request.getAttribute("currentUserId");
        
        List<RankingEntryResponse> topPlayers = profileService.getGlobalRanking(limit);
        Integer myPosition = profileService.getMyRankPosition(userId);
        
        ProfileResponse myProfile = profileService.getProfileByUserId(userId);
        Long myTotalScore = myProfile.totalScore();

        RankingPageResponse response = new RankingPageResponse(topPlayers, myPosition, myTotalScore);
        return ResponseEntity.ok(response);
    }
}
