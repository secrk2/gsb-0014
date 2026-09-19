package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.FenceSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FenceScheduleRepository extends JpaRepository<FenceSchedule, Long> {

    List<FenceSchedule> findByFence_Id(Long fenceId);

    List<FenceSchedule> findByFence_Office_Id(Long officeId);
}
