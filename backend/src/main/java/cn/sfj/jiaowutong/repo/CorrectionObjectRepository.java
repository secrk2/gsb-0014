package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.CorrectionObject;
import cn.sfj.jiaowutong.domain.CorrectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorrectionObjectRepository extends JpaRepository<CorrectionObject, Long> {

    List<CorrectionObject> findByOfficeIdOrderById(Long officeId);

    List<CorrectionObject> findByOfficeIdAndStatusOrderById(Long officeId, CorrectionStatus status);

    long countByOfficeIdAndStatus(Long officeId, CorrectionStatus status);

    long countByStatus(CorrectionStatus status);

    List<CorrectionObject> findByStatusOrderById(CorrectionStatus status);
}
