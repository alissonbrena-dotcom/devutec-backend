package pe.edu.utec.devutec.repository;

import pe.edu.utec.devutec.model.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, Long> {
}