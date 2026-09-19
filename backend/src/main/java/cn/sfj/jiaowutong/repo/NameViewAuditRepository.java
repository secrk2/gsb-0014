package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.NameViewAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NameViewAuditRepository extends JpaRepository<NameViewAudit, Long> {

    List<NameViewAudit> findByOffenderIdOrderByViewedAtDescIdDesc(Long offenderId);
}
