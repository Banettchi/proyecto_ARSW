package com.shark.profile.controller;

import com.shark.profile.dto.ProfileCreateDto;
import com.shark.profile.dto.events.UserRegisteredEventPayload;
import com.shark.profile.service.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/profiles/internal")
public class InternalProfileController {

    private final ProfileService profileService;

    public InternalProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /*
     * Este endpoint HTTP existe como mecanismo de recuperación manual 
     * (ej: reprocesar un mensaje desde la DLQ a mano) y NO como el 
     * camino principal de creación de perfiles. El flujo normal es 
     * vía el consumer de RabbitMQ (UserRegisteredListener).
     */
    @PostMapping("/create")
    public ResponseEntity<Void> createInitialProfile(@RequestBody ProfileCreateDto dto) {
        UserRegisteredEventPayload payload = new UserRegisteredEventPayload(
                dto.userId(), 
                dto.username(), 
                "recovery@internal.local", 
                Instant.now()
        );
        profileService.createInitialProfile(payload);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
