package pe.edu.utec.devutec.assets;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utec.devutec.exceptions.ApiException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProjectAssetController {

    private final ProjectAssetService service;

    // Sube un logo, foto o PDF (multipart, campo "file"). Queda sin proyecto hasta que el cliente lo publica.
    @PostMapping(value = "/project-assets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ProjectAssetDTO> upload(@RequestParam("file") MultipartFile file) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(file.getBytes(), file.getOriginalFilename()));
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "No pudimos recibir el archivo. Inténtalo de nuevo");
        }
    }

    @DeleteMapping("/project-assets/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{projectId}/assets")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ProjectAssetsDTO> attach(@PathVariable Long projectId, @Valid @RequestBody AttachAssetsDTO request) {
        return ResponseEntity.ok(service.attach(projectId, request.assetIds()));
    }

    @GetMapping("/projects/{projectId}/assets")
    public ResponseEntity<ProjectAssetsDTO> list(@PathVariable Long projectId) {
        return ResponseEntity.ok(service.list(projectId));
    }

    // Descarga segura: siempre como archivo adjunto (el navegador no lo abre dentro de DEVUTEC), con su tipo real
    // y sin que el navegador adivine otro tipo. Así un archivo no puede ejecutar código en la página.
    @GetMapping("/project-assets/{id}/file")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        ProjectAsset asset = service.download(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(asset.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(asset.getFileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(asset.getData());
    }
}
