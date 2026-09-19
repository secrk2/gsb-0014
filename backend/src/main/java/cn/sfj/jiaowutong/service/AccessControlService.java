package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.CorrectionObject;
import cn.sfj.jiaowutong.domain.Role;
import cn.sfj.jiaowutong.repo.CorrectionObjectRepository;
import cn.sfj.jiaowutong.security.LoginUser;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据范围与对象间隔离：
 * - 矫正对象：只能访问本人档案，看他人一律 403 错误态；
 * - 司法所干警：只能访问本所对象，跨所一律 403；
 * - 监管员（区级）：全区可读。
 */
@Service
public class AccessControlService {

    private final CorrectionObjectRepository objectRepository;

    public AccessControlService(CorrectionObjectRepository objectRepository) {
        this.objectRepository = objectRepository;
    }

    public CorrectionObject loadVisible(Long objectId, LoginUser user) {
        CorrectionObject obj = objectRepository.findById(objectId)
                .orElseThrow(() -> ApiException.notFound("档案不存在或已归档（编号：" + objectId + "）"));
        assertCanView(obj, user);
        return obj;
    }

    public void assertCanView(CorrectionObject obj, LoginUser user) {
        if (user.role() == Role.OFFENDER) {
            if (user.offenderId() == null || !user.offenderId().equals(obj.getId())) {
                // 对象间隔离：返回错误态（403）而不是空白页
                throw ApiException.forbidden("对象间数据相互隔离：您只能查看本人矫正档案，无权查看他人档案");
            }
            return;
        }
        if (user.role() == Role.STAFF) {
            if (user.officeId() == null || !user.officeId().equals(obj.getOffice().getId())) {
                throw ApiException.forbidden(
                        "越权访问：该对象归属「" + obj.getOffice().getName() + "」，不在您所在司法所的管辖范围内");
            }
        }
        // SUPERVISOR 区级，放行
    }

    /** 按数据范围过滤档案列表 */
    public List<CorrectionObject> filterByScope(List<CorrectionObject> all, LoginUser user) {
        if (user.role() == Role.SUPERVISOR) {
            return all;
        }
        if (user.role() == Role.STAFF) {
            return all.stream().filter(o -> user.officeId().equals(o.getOffice().getId())).toList();
        }
        return all.stream()
                .filter(o -> user.offenderId() != null && user.offenderId().equals(o.getId()))
                .toList();
    }

    public void assertStaffOrSupervisor(LoginUser user) {
        if (user.role() == Role.OFFENDER) {
            throw ApiException.forbidden("矫正对象账号无权执行该管理操作");
        }
    }
}
