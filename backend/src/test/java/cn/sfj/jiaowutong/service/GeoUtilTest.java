package cn.sfj.jiaowutong.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoUtilTest {

    @Test
    void distanceIsSymmetricAndReasonable() {
        // 北京天安门 ~ 故宫神武门约 0.6km
        double d1 = GeoUtil.distanceMeters(39.9087, 116.3975, 39.9163, 116.3972);
        double d2 = GeoUtil.distanceMeters(39.9163, 116.3972, 39.9087, 116.3975);
        assertEquals(d1, d2, 1e-6);
        assertTrue(d1 > 500 && d1 < 1000, "实际距离=" + d1);
    }

    @Test
    void circleFence() {
        assertTrue(GeoUtil.isInsideCircle(30.21230, 114.32456, 30.21230, 114.32456, 1000));
        // 约 2km 外在圆外
        assertFalse(GeoUtil.isInsideCircle(30.23230, 114.32456, 30.21230, 114.32456, 1000));
    }

    @Test
    void polygonContainsInteriorAndExcludesOutside() {
        // 正方形：(0,0)-(0,10)-(10,10)-(10,0)，纬经度
        double[] lats = {0, 0, 10, 10};
        double[] lngs = {0, 10, 10, 0};
        assertTrue(GeoUtil.isInsidePolygon(5, 5, lats, lngs));
        assertFalse(GeoUtil.isInsidePolygon(15, 5, lats, lngs));
        assertFalse(GeoUtil.isInsidePolygon(-1, -1, lats, lngs));
    }

    @Test
    void concavePolygonUsesRayCastingNotBoundingBox() {
        // 顶点序 (lat,lng)：下段 lat 0..5 横跨 lng 0..10；上段 lat 5..10 仅 lng 5..10。
        // 凹口在左上角（lat>5 且 lng<5）：该点落在外接矩形内但在多边形外，射线法必须判外。
        double[] lats = {0, 0, 10, 10, 5, 5};
        double[] lngs = {0, 10, 10, 5, 5, 0};
        assertTrue(GeoUtil.isInsidePolygon(2, 2, lats, lngs), "下段实心区应在内");
        assertTrue(GeoUtil.isInsidePolygon(2, 7, lats, lngs), "下段实心区应在内");
        assertTrue(GeoUtil.isInsidePolygon(7, 7, lats, lngs), "上段右半实心区应在内");
        assertFalse(GeoUtil.isInsidePolygon(7, 2, lats, lngs), "左上凹口应判为多边形外");
    }

    @Test
    void polygonRejectsMalformedInput() {
        assertFalse(GeoUtil.isInsidePolygon(1, 1, new double[]{0, 1}, new double[]{0, 1}));
    }

    @Test
    void speedFlagsImplausibleJumpButNotWalking() {
        // 步行：5 秒走 7 米 = 1.4 m/s，合理
        double walk = GeoUtil.speedMps(GeoUtil.distanceMeters(30.0, 114.0, 30.000063, 114.0), 5);
        assertTrue(walk >= 0 && walk < 45, "步行速度=" + walk);

        // 漂移：5 秒跳到 3km 外 = 600 m/s，超阈值
        double jump = GeoUtil.speedMps(GeoUtil.distanceMeters(30.0, 114.0, 30.027, 114.0), 5);
        assertTrue(jump > 45, "跳变速度=" + jump);

        assertEquals(-1, GeoUtil.speedMps(100, 0), "非正时间差应返回 -1");
    }
}
