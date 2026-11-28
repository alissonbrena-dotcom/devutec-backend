package pe.edu.utec.devutec.metrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MetricDailyCountRepository extends JpaRepository<MetricDailyCount, Long> {

    Optional<MetricDailyCount> findBySiteIdAndDayAndTypeAndTest(Long siteId, LocalDate day, MetricType type, boolean test);

    List<MetricDailyCount> findBySiteIdAndDayGreaterThanEqual(Long siteId, LocalDate since);

    @Modifying
    @Query("delete from MetricDailyCount c where c.siteId = :siteId")
    void deleteBySiteId(@Param("siteId") Long siteId);
}
