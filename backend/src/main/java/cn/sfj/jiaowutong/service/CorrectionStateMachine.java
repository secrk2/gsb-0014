package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.CorrectionStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 矫正状态机：
 * <pre>
 * 入矫登记 ──→ 在矫 ──→ 请假外出 ──→ 在矫（销假）
 *               │  └──→ 训诫 ──→ 在矫（教育恢复）
 *               │        └──→ 收监（终态）
 *               ├──→ 收监（终态）
 *               └──→ 解除（终态）
 * 请假外出 ──→ 训诫（逾假不归等）
 * </pre>
 * 收监/解除为终态；任何终态回退、跨阶段跳跃均被拦下并给出原因。
 */
public final class CorrectionStateMachine {

    private static final Map<CorrectionStatus, Set<CorrectionStatus>> ALLOWED = new EnumMap<>(CorrectionStatus.class);

    static {
        ALLOWED.put(CorrectionStatus.INTAKE, EnumSet.of(CorrectionStatus.SERVING));
        ALLOWED.put(CorrectionStatus.SERVING,
                EnumSet.of(CorrectionStatus.LEAVE, CorrectionStatus.ADMONISHED,
                        CorrectionStatus.REIMPRISONED, CorrectionStatus.RELEASED));
        ALLOWED.put(CorrectionStatus.LEAVE, EnumSet.of(CorrectionStatus.SERVING, CorrectionStatus.ADMONISHED));
        ALLOWED.put(CorrectionStatus.ADMONISHED,
                EnumSet.of(CorrectionStatus.SERVING, CorrectionStatus.REIMPRISONED));
        ALLOWED.put(CorrectionStatus.REIMPRISONED, EnumSet.noneOf(CorrectionStatus.class));
        ALLOWED.put(CorrectionStatus.RELEASED, EnumSet.noneOf(CorrectionStatus.class));
    }

    private CorrectionStateMachine() {
    }

    public static boolean canTransition(CorrectionStatus from, CorrectionStatus to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** 非法流转直接抛 INVALID_TRANSITION，消息说明原因与合法去向。 */
    public static void assertTransition(CorrectionStatus from, CorrectionStatus to) {
        if (from == to) {
            throw new ApiException("INVALID_TRANSITION",
                    "状态未发生变化，对象当前已是「" + from.getLabel() + "」");
        }
        if (from == CorrectionStatus.RELEASED) {
            throw new ApiException("INVALID_TRANSITION",
                    "非法回退：矫正已解除，档案为终态归档，不允许再做任何状态变更");
        }
        if (from == CorrectionStatus.REIMPRISONED) {
            throw new ApiException("INVALID_TRANSITION",
                    "非法回退：对象已收监执行，社区矫正流程终结，不能回退到监外状态");
        }
        if (to == CorrectionStatus.INTAKE) {
            throw new ApiException("INVALID_TRANSITION",
                    "非法回退：入矫登记是流程起点，对象已进入后续流程，不能退回重新入矫");
        }
        if (!canTransition(from, to)) {
            String reason = switch (from) {
                case INTAKE -> "入矫登记完成前对象尚未纳入在矫管理，须先办理入矫宣告进入「在矫」";
                case LEAVE -> "请假外出期间只能「销假返所在矫」或因逾假违规予以「训诫」，不能直接变更为该状态";
                case ADMONISHED -> "训诫期内仅可在教育改正后恢复「在矫」，或情节严重予以「收监」";
                default -> "当前状态不允许该变更";
            };
            String allowed = ALLOWED.get(from).stream()
                    .map(CorrectionStatus::getLabel)
                    .reduce((a, b) -> a + "、" + b)
                    .orElse("无");
            throw new ApiException("INVALID_TRANSITION",
                    "非法状态回退/跳转：「" + from.getLabel() + "」→「" + to.getLabel()
                            + "」。原因：" + reason + "。当前允许的下一状态：" + allowed);
        }
    }
}
