package com.muq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 뮤큐 서버의 시작점.
 * 이 클래스가 놓인 com.muq 패키지가 컴포넌트 스캔의 기준이 되므로,
 * 모든 도메인 패키지(room, track, ...)는 반드시 이 아래에 둔다.
 */
@SpringBootApplication
public class MuqApplication {

    public static void main(String[] args) {
        SpringApplication.run(MuqApplication.class, args);
    }
}
