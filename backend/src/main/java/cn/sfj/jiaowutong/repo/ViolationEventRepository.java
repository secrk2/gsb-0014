package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.ViolationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ViolationEventRepository extends JpaRepository<ViolationEvent, Long> {

    long countByOfficeIdAndReadFlagFalse(Long officeId);

    List<ViolationEvent> findTop10ByOfficeIdAndReadFlagFalseOrderByEventTimeDesc(Long officeId);

    List<ViolationEvent> findTop20ByOffender_IdOrderByEventTimeDesc(Long offenderId);
}
