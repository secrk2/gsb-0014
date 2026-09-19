package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.ActivityAttendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityAttendanceRepository extends JpaRepository<ActivityAttendance, Long> {

    List<ActivityAttendance> findByActivity_IdOrderByRecordedAtAscIdAsc(Long activityId);

    List<ActivityAttendance> findByOffender_IdOrderByRecordedAtDescIdDesc(Long offenderId);

    /** 某对象在某活动的打卡记录（用于判断本人是否已打卡及结果） */
    Optional<ActivityAttendance> findByActivity_IdAndOffender_Id(Long activityId, Long offenderId);

    /** 某对象在某活动是否已有打卡记录（一人一活动只打一次卡） */
    boolean existsByActivity_IdAndOffender_Id(Long activityId, Long offenderId);
}
