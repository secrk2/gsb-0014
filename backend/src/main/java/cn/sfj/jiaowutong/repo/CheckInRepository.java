package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.CheckIn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    boolean existsByOffender_IdAndCheckDate(Long offenderId, LocalDate checkDate);

    List<CheckIn> findByOffender_IdAndCheckDate(Long offenderId, LocalDate checkDate);

    List<CheckIn> findByOffender_IdOrderByCheckDateAscIdAsc(Long offenderId);

    /** 月度当面报到去重：同一对象同一月是否已有 IN_PERSON 登记（不依赖具体哪一天登记） */
    boolean existsByOffender_IdAndMethodAndCheckDateBetween(Long offenderId, String method,
                                                            LocalDate from, LocalDate to);

    List<CheckIn> findByOffender_IdAndMethodAndCheckDateBetween(Long offenderId, String method,
                                                                LocalDate from, LocalDate to);
}
