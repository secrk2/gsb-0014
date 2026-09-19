package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.TrackPoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface TrackPointRepository extends JpaRepository<TrackPoint, Long> {

    /** 幂等判断：同一对象同一客户端点 ID 已接收过（含已判漂移丢弃的点） */
    boolean existsByOffender_IdAndClientPointId(Long offenderId, String clientPointId);

    List<TrackPoint> findByOffender_IdOrderByPointTimeAscIdAsc(Long offenderId);

    /** 有效轨迹：漂移丢弃点不参与轨迹连线与最新位置 */
    List<TrackPoint> findByOffender_IdAndResultOrderByPointTimeAscIdAsc(
            Long offenderId, TrackPoint.IngestResult result);

    List<TrackPoint> findByOffender_IdAndResultAndPointTimeBetweenOrderByPointTimeAscIdAsc(
            Long offenderId, TrackPoint.IngestResult result, Instant from, Instant to);

    List<TrackPoint> findByOffender_IdAndResultAndPointTimeAfterOrderByPointTimeAscIdAsc(
            Long offenderId, TrackPoint.IngestResult result, Instant from);

    long countByOffender_IdAndResult(Long offenderId, TrackPoint.IngestResult result);

    long countByOffender_IdAndResultAndPointTimeAfter(
            Long offenderId, TrackPoint.IngestResult result, Instant after);

    long countByOffender_IdAndResultAndPointTimeBetween(
            Long offenderId, TrackPoint.IngestResult result, Instant from, Instant to);

    long deleteByOffender_Id(Long offenderId);
}
