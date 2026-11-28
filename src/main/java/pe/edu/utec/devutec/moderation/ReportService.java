package pe.edu.utec.devutec.moderation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utec.devutec.auth.domain.User;
import pe.edu.utec.devutec.auth.infrastructure.UserRepository;
import pe.edu.utec.devutec.exceptions.ForbiddenOperationException;
import pe.edu.utec.devutec.exceptions.InvalidOperationException;
import pe.edu.utec.devutec.exceptions.ResourceNotFoundException;
import pe.edu.utec.devutec.model.application.Application;
import pe.edu.utec.devutec.model.contract.Contract;
import pe.edu.utec.devutec.repository.ApplicationRepository;
import pe.edu.utec.devutec.repository.ContractRepository;
import pe.edu.utec.devutec.service.CurrentUserService;
import pe.edu.utec.devutec.support.ContactFilter;

import java.util.List;

// Reportes para el admin. Una persona reporta a la otra parte de su contrato o postulación (el estudiante es el más
// interesado: si cobra por fuera pierde la garantía y la reseña). El filtro de contacto también deja un reporte
// automático cada vez que bloquea un texto, así el admin ve quién lo intenta seguido.
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final ContractRepository contractRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public ReportResponseDTO create(ReportCreateDTO dto) {
        if (dto.reason() == ReportReason.FILTRO_AUTOMATICO) {
            throw new InvalidOperationException("Elige otro motivo para el reporte");
        }
        if ((dto.contractId() == null) == (dto.applicationId() == null)) {
            throw new InvalidOperationException("Indica el contrato o la postulación que quieres reportar");
        }
        User reporter = currentUserService.getCurrentUser();
        Report report = new Report();
        report.setReporter(reporter);
        report.setReason(dto.reason());
        report.setDetail(dto.detail() == null || dto.detail().isBlank() ? null : dto.detail().trim());

        Application application;
        if (dto.contractId() != null) {
            Contract contract = contractRepository.findById(dto.contractId())
                    .orElseThrow(() -> new ResourceNotFoundException("Contrato no encontrado con id: " + dto.contractId()));
            application = contract.getApplication();
            report.setPlace("Contrato " + contract.getId() + " · " + application.getProject().getTitle());
        } else {
            application = applicationRepository.findById(dto.applicationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Postulación no encontrada con id: " + dto.applicationId()));
            report.setPlace("Postulación " + application.getId() + " · " + application.getProject().getTitle());
        }
        report.setReported(otherPerson(application, reporter));
        return toResponse(reportRepository.save(report));
    }

    // Lo llama el filtro antes de rechazar un texto. Va en su propia transacción para que quede guardado
    // aunque la operación original se cancele.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordBlocked(User author, String place, String text) {
        Report report = new Report();
        report.setReported(userRepository.getReferenceById(author.getId()));
        report.setReason(ReportReason.FILTRO_AUTOMATICO);
        report.setPlace(place.length() > 120 ? place.substring(0, 120) : place);
        String clean = ContactFilter.removeContactData(text == null ? "" : text.trim());
        report.setDetail(clean.length() > 1000 ? clean.substring(0, 1000) : clean);
        reportRepository.save(report);
    }

    @Transactional(readOnly = true)
    public List<ReportResponseDTO> findLatest() {
        return reportRepository.findTop100ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public ReportResponseDTO markReviewed(Long id) {
        Report report = reportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reporte no encontrado con id: " + id));
        report.setStatus(ReportStatus.REVIEWED);
        return toResponse(report);
    }

    // La otra persona: si reporta el cliente, el estudiante; si reporta el estudiante, el cliente
    private User otherPerson(Application application, User reporter) {
        User freelancer = application.getFreelancer().getUser();
        Long clientId = application.getProject().getClientId();
        if (reporter.getId().equals(clientId)) {
            return freelancer;
        }
        if (reporter.getId().equals(freelancer.getId())) {
            return userRepository.findById(clientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cliente no encontrado"));
        }
        throw new ForbiddenOperationException("Solo puedes reportar a personas de tus contratos o postulaciones");
    }

    private ReportResponseDTO toResponse(Report report) {
        User reported = report.getReported();
        return new ReportResponseDTO(
                report.getId(),
                report.getReporter() == null ? "Filtro automático" : report.getReporter().getNombre(),
                reported.getId(),
                reported.getNombre(),
                reportRepository.countByReportedId(reported.getId()),
                report.getReason(),
                report.getPlace(),
                report.getDetail(),
                report.getStatus(),
                report.getCreatedAt());
    }
}
