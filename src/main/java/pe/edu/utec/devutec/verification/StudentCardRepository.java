package pe.edu.utec.devutec.verification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentCardRepository extends JpaRepository<StudentCard, Long> {

    List<StudentCard> findByStatusOrderBySubmittedAtAsc(CarnetStatus status);

    List<StudentCard> findAllByOrderBySubmittedAtDesc();
}
