package pe.edu.utec.devutec.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FuncionalidadReferenciaRepository extends JpaRepository<FuncionalidadReferencia, String> {

    List<FuncionalidadReferencia> findAllByOrderByCategoriaAscCodigoAsc();
}
