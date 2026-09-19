package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.LeaveApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface LeaveApplicationRepository extends JpaRepository<LeaveApplication, Long> {

    /** 对象本人的请假单，按时间倒序 */
    List<LeaveApplication> findByOffender_IdOrderByCreatedAtDescIdDesc(Long offenderId);

    /** 司法所待初审 / 或某所按状态查（本所干警视角） */
    List<LeaveApplication> findByOfficeIdAndStatusOrderBySubmittedAtAscIdAsc(Long officeId, LeaveApplication.Status status);

    /** 区局复核工作台：所有处于待区局复核的单子 */
    List<LeaveApplication> findByStatusOrderByOfficeApprovedAtAscIdAsc(LeaveApplication.Status status);

    /** 全部审批中/假期中的单子（区局总览） */
    List<LeaveApplication> findByStatusInOrderByCreatedAtDescIdDesc(List<LeaveApplication.Status> statuses);

    /** 司法所视角的审批列表（按状态集合） */
    List<LeaveApplication> findByOfficeIdAndStatusInOrderByCreatedAtDescIdDesc(Long officeId, List<LeaveApplication.Status> statuses);

    /** 对象是否存在尚未终结（审批中或准假中）的请假单：同一时间不允许重复请假 */
    boolean existsByOffender_IdAndStatusIn(Long offenderId, List<LeaveApplication.Status> statuses);

    /** 定时任务：悲观锁扫描已批准且到期未销假的单子 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<LeaveApplication> findByStatusAndEndTimeBefore(LeaveApplication.Status status, java.time.Instant at);

    Optional<LeaveApplication> findFirstByOffender_IdAndStatusOrderByEndTimeDescIdDesc(
            Long offenderId, LeaveApplication.Status status);
}
