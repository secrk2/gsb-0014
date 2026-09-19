package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.GeoFence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GeoFenceRepository extends JpaRepository<GeoFence, Long> {

    List<GeoFence> findByOffice_IdAndEnabledTrue(Long officeId);
}
