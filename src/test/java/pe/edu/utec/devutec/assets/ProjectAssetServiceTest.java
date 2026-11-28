package pe.edu.utec.devutec.assets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.utec.devutec.auth.domain.Role;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.exceptions.ApiException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.ProjectStatus;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.repository.ProjectRepository;
import pe.edu.utec.devutec.service.CurrentUserService;
import pe.edu.utec.devutec.support.AiGateway;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectAssetServiceTest {

    @Mock private ProjectAssetRepository assets;
    @Mock private ProjectRepository projects;
    @Mock private ContractRepository contracts;
    @Mock private CurrentUserService currentUserService;
    @Mock private AssetFileProcessor processor;
    @Mock private AiGateway aiGateway;
    @InjectMocks private ProjectAssetService service;

    private final User client = user(1L, Role.CLIENT);
    private final User hired = user(2L, Role.FREELANCER);
    private final User otherStudent = user(3L, Role.FREELANCER);
    private Project project;
    private ProjectAsset logo;

    @BeforeEach
    void setUp() {
        project = new Project();
        project.setId(10L);
        project.setClientId(1L);
        project.setStatus(ProjectStatus.OPEN);
        logo = new ProjectAsset();
        logo.setId(100L);
        logo.setOwnerId(1L);
        logo.setProjectId(10L);
        logo.setKind(ProjectAssetKind.IMAGE);
        logo.setContentType("image/png");
        logo.setFileName("logo.png");
        logo.setData(new byte[]{1, 2, 3});
        logo.setSizeBytes(3);
        logo.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void uploadSavesCleanFileWithAiSummaryWithoutContactData() {
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(processor.process(any())).thenReturn(new AssetFileProcessor.ProcessedFile(
                ProjectAssetKind.IMAGE, new byte[]{9}, "image/png", "png", null, null));
        when(aiGateway.replyStructuredWithImages(anyString(), anyString(), anyList(), eq(AssetAnalysis.class)))
                .thenReturn(new AssetAnalysis("LOGO", "Logo rojo de pizzería. Llamar al 987654321",
                        List.of("#c0392b", "rojo")));
        when(assets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectAssetDTO dto = service.upload(new byte[]{1}, "C:\\fotos\\Mi logo<script>.svg.png");

        assertThat(dto.fileName()).isEqualTo("Mi logoscriptsvg.png");
        assertThat(dto.summary()).startsWith("logo: Logo rojo de pizzería").contains("#C0392B").doesNotContain("987654321");
    }

    @Test
    void uploadWorksEvenIfAiFails() {
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(processor.process(any())).thenReturn(new AssetFileProcessor.ProcessedFile(
                ProjectAssetKind.IMAGE, new byte[]{9}, "image/jpeg", "jpg", null, null));
        when(aiGateway.replyStructuredWithImages(anyString(), anyString(), anyList(), eq(AssetAnalysis.class)))
                .thenThrow(new ApiException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "no"));
        when(assets.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.upload(new byte[]{1}, "foto.jpg").summary()).isNull();
    }

    @Test
    void onlyOnePdfWhileBuildingTheProject() {
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(processor.process(any())).thenReturn(new AssetFileProcessor.ProcessedFile(
                ProjectAssetKind.PDF, new byte[]{9}, "application/pdf", "pdf", 1, "Carta"));
        when(assets.countByOwnerIdAndProjectIdIsNullAndKind(1L, ProjectAssetKind.PDF)).thenReturn(1L);

        assertThatThrownBy(() -> service.upload(new byte[]{1}, "carta.pdf")).isInstanceOf(ApiException.class)
                .hasMessageContaining("solo 1 PDF");
        verify(assets, never()).save(any());
    }

    @Test
    void studentsCannotUpload() {
        when(currentUserService.getCurrentUser()).thenReturn(hired);
        assertThatThrownBy(() -> service.upload(new byte[]{1}, "x.png")).isInstanceOf(ApiException.class);
        verify(processor, never()).process(any());
    }

    @Test
    void hiredStudentCanDownloadButOthersOnlySeeCounts() {
        when(assets.findById(100L)).thenReturn(Optional.of(logo));
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(contracts.existsByApplication_Project_IdAndApplication_Freelancer_User_Id(10L, 2L)).thenReturn(true);

        when(currentUserService.getCurrentUser()).thenReturn(hired);
        assertThat(service.download(100L).getData()).containsExactly(1, 2, 3);

        when(currentUserService.getCurrentUser()).thenReturn(otherStudent);
        assertThatThrownBy(() -> service.download(100L)).isInstanceOf(ResourceNotFoundException.class);

        when(assets.countByProjectIdAndKind(10L, ProjectAssetKind.IMAGE)).thenReturn(1L);
        ProjectAssetsDTO seen = service.list(10L);
        assertThat(seen.canDownload()).isFalse();
        assertThat(seen.images()).isEqualTo(1);
        assertThat(seen.files()).isEmpty();
    }

    @Test
    void attachRejectsFilesOfAnotherClient() {
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(assets.findByIdInAndOwnerId(List.of(100L, 200L), 1L)).thenReturn(List.of(logo));

        assertThatThrownBy(() -> service.attach(10L, List.of(100L, 200L))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void attachLinksPendingFilesToTheProject() {
        logo.setProjectId(null);
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(assets.findByIdInAndOwnerId(List.of(100L), 1L)).thenReturn(List.of(logo));

        service.attach(10L, List.of(100L));

        assertThat(logo.getProjectId()).isEqualTo(10L);
    }

    @Test
    void cannotDeleteOnceTheProjectStarted() {
        project.setStatus(ProjectStatus.IN_PROGRESS);
        when(currentUserService.getCurrentUser()).thenReturn(client);
        when(assets.findById(100L)).thenReturn(Optional.of(logo));
        when(projects.findById(10L)).thenReturn(Optional.of(project));

        assertThatThrownBy(() -> service.delete(100L)).isInstanceOf(ApiException.class).hasMessageContaining("ya empezó");
    }

    @Test
    void assistantOnlyReadsTheClientsOwnFiles() {
        logo.setAiSummary("logo: rojo");
        when(assets.findByIdInAndOwnerId(List.of(100L, 999L), 1L)).thenReturn(List.of(logo));

        assertThat(service.describeForAssistant(1L, List.of(100L, 999L))).isEqualTo("- Imagen \"logo.png\": logo: rojo");
        assertThat(service.describeForAssistant(1L, null)).isEmpty();
    }

    private static User user(Long id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRol(role);
        return user;
    }
}
