package cn.sfj.jiaowutong.web.vo;

import java.util.List;

public record ObjectDetailView(ObjectView object,
                               List<TransitionView> transitions,
                               List<DashboardView.RedDotItem> violations,
                               boolean checkedToday,
                               long trackCount) {
}
