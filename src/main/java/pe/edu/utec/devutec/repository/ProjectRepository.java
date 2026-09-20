package pe.edu.utec.devutec.repository;

import pe.edu.utec.devutec.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}