package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.MonitorAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonitorActionRepository extends JpaRepository<MonitorAction, Long> {

    List<MonitorAction> findByOffenderIdOrderByCreatedAtDescIdDesc(Long offenderId);

    Optional<MonitorAction> findFirstByOffenderIdAndActionOrderByCreatedAtDescIdDesc(
            Long offenderId, String action);

    boolean existsByOffenderIdAndActionAndPointId(Long offenderId, String action, Long pointId);

    List<MonitorAction> findByOffenderIdAndActionAndPointIdIn(
            Long offenderId, String action, List<Long> pointIds);
}
