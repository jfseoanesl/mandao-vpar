package com.mandao.evidence;

import com.mandao.shared.AuthUser;
import com.mandao.shared.FileResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class FileController {

    private final EvidenceService evidenceService;

    public FileController(EvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping("/api/files/{id}")
    public ResponseEntity<byte[]> file(@PathVariable("id") UUID id) {
        EvidenceService.EvidenceFile file = evidenceService.file(id, AuthUser.current());
        return FileResponses.inline(file.data(), file.contentType());
    }
}
