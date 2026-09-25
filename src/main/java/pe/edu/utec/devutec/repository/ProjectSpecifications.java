package pe.edu.utec.devutec.repository;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.edu.utec.devutec.dto.ProjectFilterDTO;
import pe.edu.utec.devutec.model.Project;
import pe.edu.utec.devutec.model.Skill;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class ProjectSpecifications {

    private ProjectSpecifications() {
    }

    public static Specification<Project> withFilters(ProjectFilterDTO filters) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filters.getSearch() != null && !filters.getSearch().isBlank()) {
                String pattern = "%" + filters.getSearch().trim().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.<String>get("title")), pattern));
            }
            if (filters.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), filters.getStatus()));
            }
            if (filters.getSkillId() != null) {
                Join<Project, Skill> skills = root.join("skills");
                predicates.add(cb.equal(skills.get("id"), filters.getSkillId()));
                if (query != null) {
                    query.distinct(true);
                }
            }
            if (filters.getMinBudget() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("budget"), filters.getMinBudget()));
            }
            if (filters.getMaxBudget() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("budget"), filters.getMaxBudget()));
            }
            if (filters.getDeadlineBefore() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("deadline"), filters.getDeadlineBefore()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}