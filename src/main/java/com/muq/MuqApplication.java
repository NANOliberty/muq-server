package com.muq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 뮤큐 서버의 시작점.
 * 이 클래스가 놓인 com.muq 패키지가 컴포넌트 스캔의 기준이 되므로,
 * 모든 도메인 패키지(room, track, ...)는 반드시 이 아래에 둔다.
 */
@SpringBootApplication
// 왜: @ConfigurationProperties 가 붙은 클래스(RoomProperties 등)를 찾아 빈으로 등록한다.
@ConfigurationPropertiesScan
public class MuqApplication {

    public static void main(String[] args) {
        SpringApplication.run(MuqApplication.class, args);
    }
}
