package pe.edu.utec.devutec.assets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ProjectAssetRepository extends JpaRepository<ProjectAsset, Long> {

    List<ProjectAsset> findByProjectIdOrderByIdAsc(Long projectId);

    List<ProjectAsset> findByIdInAndOwnerId(List<Long> ids, Long ownerId);

    long countByOwnerIdAndProjectIdIsNullAndKind(Long ownerId, ProjectAssetKind kind);

    long countByProjectIdAndKind(Long projectId, ProjectAssetKind kind);

    long countByOwnerIdAndCreatedAtAfter(Long ownerId, LocalDateTime since);
}
