package cn.sfj.jiaowutong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class JiaowutongApplication {
    public static void main(String[] args) {
        // 存储与传输一律 UTC；默认时区固定 UTC，避免容器 TZ 污染“今天/星期几”判定，
        // 业务层需要本地日历时显式按司法所 ZoneId 转换
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(JiaowutongApplication.class, args);
    }
}
