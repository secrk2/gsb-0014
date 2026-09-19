package cn.sfj.jiaowutong.domain;

/**
 * 矫正全流程状态机。
 * 流转规则见 {@link cn.sfj.jiaowutong.service.CorrectionStateMachine}
 */
public enum CorrectionStatus {
    INTAKE("入矫登记"),
    SERVING("在矫"),
    LEAVE("请假外出"),
    ADMONISHED("训诫"),
    REIMPRISONED("收监"),
    RELEASED("解除");

    private final String label;

    CorrectionStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
