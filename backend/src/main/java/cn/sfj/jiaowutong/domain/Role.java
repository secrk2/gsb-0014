package cn.sfj.jiaowutong.domain;

/**
 * 系统角色：
 * SUPERVISOR  监管员（区司法局，可看全区对象，但全名需二次确认）
 * STAFF       司法所干警（仅本所对象，可处置本所对象）
 * OFFENDER    矫正对象本人（仅可报到、上报位置、看自己的有限信息）
 */
public enum Role {
    SUPERVISOR("监管员"),
    STAFF("司法所干警"),
    OFFENDER("矫正对象");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
