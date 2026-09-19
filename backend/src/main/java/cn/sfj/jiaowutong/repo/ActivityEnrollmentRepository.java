package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.ActivityEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityEnrollmentRepository extends JpaRepository<ActivityEnrollment, Long> {

    /** 对象在某活动的报名记录（含已取消），用于唯一报名校验 */
    Optional<ActivityEnrollment> findByActivity_IdAndOffender_Id(Long activityId, Long offenderId);

    List<ActivityEnrollment> findByActivity_IdOrderByEnrolledAtAscIdAsc(Long activityId);

    long countByActivity_IdAndStatus(Long activityId, String status);

    List<ActivityEnrollment> findByOffender_IdOrderByEnrolledAtDescIdDesc(Long offenderId);
}
