package pe.edu.utec.devutec.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.edu.utec.devutec.model.FreelancerProfile;

import java.util.Optional;

public interface FreelancerProfileRepository extends JpaRepository<FreelancerProfile, Long> {
    Optional<FreelancerProfile> findByUserId(Long userId);

    // Tarifa por hora promedio de los estudiantes que la llenaron; null si nadie la llenó
    @Query("select avg(f.tarifaHora) from FreelancerProfile f where f.tarifaHora is not null and f.tarifaHora > 0")
    Double averageHourlyRate();

}
