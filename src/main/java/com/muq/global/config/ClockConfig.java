package com.muq.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 현재 시각의 출처를 빈 하나로 통일한다.
 * 코드가 Instant.now() 를 직접 부르면 테스트에서 시간을 고정할 수 없다. Clock 을 주입받으면 테스트는 고정 시계로 바꿔 끼울 수 있다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
