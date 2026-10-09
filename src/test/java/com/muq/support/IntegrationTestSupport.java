package com.muq.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 통합 테스트의 공통 부모. 실제 PostgreSQL·Redis 컨테이너를 띄우고 스프링 전체를 올린다.
 * 통합 테스트 클래스는 이 클래스를 상속하기만 하면 된다.
 * H2 나 임베디드 Redis 로 대체하지 않는 이유: Flyway SQL, Lua 스크립트, 분산락은 실제 제품에서만 같은 동작을 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestSupport {

    // 왜 @ServiceConnection: 컨테이너의 임의 포트를 읽어 datasource·redis 접속 정보로 자동 주입한다.
    //   application.yml 의 localhost 기본값이나 .env 값보다 우선하므로 테스트가 로컬 DB 를 건드리지 않는다.
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // 왜 name = "redis": GenericContainer 는 어떤 서비스인지 타입으로 알 수 없어서 이름으로 알려 준다.
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    // 왜 @Container 대신 static 블록: @Container 는 테스트 클래스마다 컨테이너를 껐다 켠다.
    //   여기서 한 번만 시작하면 JVM 이 끝날 때까지 모든 테스트 클래스가 같은 컨테이너와
    //   같은 스프링 컨텍스트(캐시)를 공유해서 전체 테스트가 훨씬 빨라진다. 종료는 Testcontainers 가 알아서 한다.
    static {
        POSTGRES.start();
        REDIS.start();
    }
}
