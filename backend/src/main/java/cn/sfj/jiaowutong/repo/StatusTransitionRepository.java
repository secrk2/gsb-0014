package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.StatusTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusTransitionRepository extends JpaRepository<StatusTransition, Long> {

    List<StatusTransition> findByOffenderIdOrderByOperatedAtDescIdDesc(Long offenderId);

    List<StatusTransition> findByOffenderIdAndToStatusOrderByOperatedAtDescIdDesc(
            Long offenderId, cn.sfj.jiaowutong.domain.CorrectionStatus toStatus);
}
