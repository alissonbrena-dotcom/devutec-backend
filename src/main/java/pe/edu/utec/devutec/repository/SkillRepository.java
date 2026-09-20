package pe.edu.utec.devutec.repository;

import pe.edu.utec.devutec.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillRepository extends JpaRepository<Skill, Long> {
}