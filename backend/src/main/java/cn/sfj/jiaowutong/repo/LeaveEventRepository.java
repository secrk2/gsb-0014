package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.LeaveEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveEventRepository extends JpaRepository<LeaveEvent, Long> {

    List<LeaveEvent> findByLeaveIdOrderByOccurredAtAscIdAsc(Long leaveId);
}
