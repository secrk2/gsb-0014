package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.PublicActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PublicActivityRepository extends JpaRepository<PublicActivity, Long> {

    /** 某司法所活动（管理端），按开始时间倒序 */
    List<PublicActivity> findByOfficeIdOrderByStartTimeDescIdDesc(Long officeId);

    /** 全部活动（区局视角 / 对象端按所过滤） */
    List<PublicActivity> findAllByOrderByStartTimeDescIdDesc();

    List<PublicActivity> findByStatusOrderByStartTimeAscIdAsc(String status);

    List<PublicActivity> findByOfficeIdAndStatusOrderByStartTimeAscIdDesc(Long officeId, String status);
}
