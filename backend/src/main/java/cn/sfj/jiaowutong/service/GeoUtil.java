package cn.sfj.jiaowutong.service;

/**
 * 地理围栏几何判定：Haversine 距离、圆形围栏、多边形（ray-casting 点面判定）。
 * 多边形顶点顺序不限，支持凸/凹多边形；顶点坐标为 [lat, lng]。
 */
public final class GeoUtil {

    private static final double EARTH_RADIUS_METERS = 6_371_000d;

    private GeoUtil() {
    }

    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public static boolean isInsideCircle(double lat, double lng,
                                         double centerLat, double centerLng, int radiusMeters) {
        return distanceMeters(lat, lng, centerLat, centerLng) <= radiusMeters;
    }

    /** 兼容旧调用名 */
    public static boolean isInside(double lat, double lng,
                                   double centerLat, double centerLng, int radiusMeters) {
        return isInsideCircle(lat, lng, centerLat, centerLng, radiusMeters);
    }

    /**
     * 点是否在多边形内（射线法，含边界处理）。
     * lats/lngs 为按顶点顺序排列的平行数组，至少 3 个点；首尾不需要重复。
     */
    public static boolean isInsidePolygon(double lat, double lng, double[] lats, double[] lngs) {
        int n = lats.length;
        if (n < 3 || lngs.length != n) {
            return false;
        }
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = lngs[i], yi = lats[i];
            double xj = lngs[j], yj = lats[j];
            boolean intersect = ((yi > lat) != (yj > lat))
                    && (lng < (xj - xi) * (lat - yi) / ((yj - yi) == 0 ? 1e-12 : (yj - yi)) + xi);
            if (intersect) {
                inside = !inside;
            }
        }
        return inside;
    }

    /**
     * 两点间等效移动速度（米/秒）。时间差非正时无法判定，返回 -1 由调用方处理。
     */
    public static double speedMps(double distanceMeters, long elapsedSeconds) {
        if (elapsedSeconds <= 0) {
            return -1;
        }
        return distanceMeters / elapsedSeconds;
    }
}
