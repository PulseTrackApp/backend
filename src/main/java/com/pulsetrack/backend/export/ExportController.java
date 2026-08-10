package com.pulsetrack.backend.export;

import java.time.LocalDate;

import com.pulsetrack.backend.common.security.AuthenticatedUser;
import com.pulsetrack.backend.export.dto.UserDataExport;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Export des donnees personnelles.
 */
@RestController
@RequestMapping("/api/v1/me/export")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    /**
     * Archive JSON complete du compte.
     *
     * <p>Servie en piece jointe datee : le navigateur la telecharge sous un nom
     * exploitable, et l'utilisateur se retrouve avec un fichier qu'il peut ranger
     * ailleurs que sur le serveur.
     */
    @GetMapping
    public ResponseEntity<UserDataExport> export(@AuthenticationPrincipal Jwt jwt) {
        UserDataExport data = exportService.export(AuthenticatedUser.idOf(jwt));

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("pulsetrack-export-" + LocalDate.now() + ".json")
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(data);
    }
}
