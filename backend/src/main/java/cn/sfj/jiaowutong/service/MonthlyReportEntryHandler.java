package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.CheckIn;
import cn.sfj.jiaowutong.domain.CorrectionObject;
import cn.sfj.jiaowutong.domain.CorrectionStatus;
import cn.sfj.jiaowutong.domain.Role;
import cn.sfj.jiaowutong.repo.CheckInRepository;
import cn.sfj.jiaowutong.repo.CorrectionObjectRepository;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.web.vo.MonthlyReportResultView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * 单条月度当面报到登记，独立事务（REQUIRES_NEW）：
 * 批量里某一人失败（越权/状态不符/当月已登记）只回滚该人，不影响其他人，
 * 从而做到“一次勾选多个对象批量完成，部分失败逐条说清谁成了、谁卡在哪”。
 */
@Service
public class MonthlyReportEntryHandler {

    private final CorrectionObjectRepository objectRepository;
    private final CheckInRepository checkInRepository;
    private final AccessControlService accessControl;

    public MonthlyReportEntryHandler(CorrectionObjectRepository objectRepository,
                                     CheckInRepository checkInRepository,
                                     AccessControlService accessControl) {
        this.objectRepository = objectRepository;
        this.checkInRepository = checkInRepository;
        this.accessControl = accessControl;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MonthlyReportResultView.Item registerOne(Long objectId, YearMonth month, LoginUser user) {
        CorrectionObject o = objectRepository.findById(objectId).orElse(null);
        if (o == null) {
            return fail(objectId, "—", "—", "档案不存在或已归档", false);
        }
        String no = o.getCorrectionNo();
        String name = o.getMaskedName();

        // 数据范围：本所干警只能登记本所；区局不限
        if (user.role() == Role.STAFF && (user.officeId() == null
                || !user.officeId().equals(o.getOffice().getId()))) {
            return fail(objectId, no, name, "不在您所在司法所管辖范围，已跳过", false);
        }

        CorrectionStatus st = o.getStatus();
        if (st == CorrectionStatus.RELEASED || st == CorrectionStatus.REIMPRISONED) {
            return fail(objectId, no, name, "对象已「" + st.getLabel() + "」，属终态，不再办理月度报到", false);
        }
        if (st == CorrectionStatus.INTAKE) {
            return fail(objectId, no, name, "尚处「入矫登记」，未纳入在矫管理，不能登记月度报到", false);
        }

        ZoneId zone = FenceService.safeZone(o.getOffice().getTimezone());
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        if (checkInRepository.existsByOffender_IdAndMethodAndCheckDateBetween(
                objectId, "IN_PERSON", monthStart, monthEnd)) {
            return fail(objectId, no, name,
                    month.getMonthValue() + " 月已登记过当面月度报到，不能重复登记", true);
        }

        LocalDate today = Instant.now().atZone(zone).toLocalDate();
        LocalDate checkDate = (!today.isBefore(monthStart) && !today.isAfter(monthEnd))
                ? today : monthEnd;
        CheckIn checkIn = new CheckIn(o, checkDate, Instant.now(), "IN_PERSON",
                o.getOffice().getCenterLat(), o.getOffice().getCenterLng(), true);
        checkIn.setRegisteredById(user.userId());
        checkIn.setRegisteredByName(user.realName());
        checkInRepository.save(checkIn);

        String suffix = st == CorrectionStatus.LEAVE ? "（对象请假外出中，按当月到所/回访情况登记）" : "";
        return new MonthlyReportResultView.Item(objectId, no, name, true,
                "已登记 " + month.getMonthValue() + " 月当面月度报到，经办人 " + user.realName() + suffix,
                false);
    }

    private MonthlyReportResultView.Item fail(Long id, String no, String name,
                                              String reason, boolean duplicated) {
        return new MonthlyReportResultView.Item(id, no, name, false, reason, duplicated);
    }
}
