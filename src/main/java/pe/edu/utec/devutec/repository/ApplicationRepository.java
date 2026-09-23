package pe.edu.utec.devutec.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.utec.devutec.model.application.AppStatus;
import pe.edu.utec.devutec.model.application.Application;

import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    boolean existsByProjectIdAndFreelancerId(Long projectId, Long freelancerId);

    List<Application> findByProjectId(Long projectId);

    List<Application> findByFreelancerId(Long freelancerId);

    List<Application> findByProjectIdAndStatus(Long projectId, AppStatus status);
}
