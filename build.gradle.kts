plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    // 왜: Spring Boot BOM을 적용해 아래 의존성 대부분의 버전을 생략할 수 있게 한다.
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.muq"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// 의존성은 마일스톤별로 추가한다. 여기 있는 것은 M0(기반)에 필요한 것뿐이다.
// security, websocket, webflux, resilience4j, oauth2-client, wiremock 은 처음 쓰는 마일스톤에서 넣는다.
dependencies {
    // --- 웹·검증·운영 ---
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // 왜 버전 명시: Spring Boot BOM 밖이다. 2.9.x 가 Boot 3.5 대응 라인 (3.x 는 Boot 4 용).
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.9.1")

    // --- PostgreSQL ---
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    // 왜: Flyway 10부터 DB별 지원이 별도 모듈로 분리됐다. 없으면 기동 시 "Unsupported Database" 로 실패한다.
    implementation("org.flywaydb:flyway-database-postgresql")

    // --- Redis ---
    // 왜 Redisson 스타터 하나만: spring-boot-starter-data-redis 를 끌고 오면서 연결 팩토리를 Redisson 으로 바꾼다.
    //   Lettuce 와 커넥션을 이중으로 열지 않고, 분산락(RLock)·Lua·Pub/Sub 를 한 클라이언트로 쓴다.
    // 왜 3.52.0: 이 버전의 기본 Spring Data 모듈이 redisson-spring-data-35 (Boot 3.5) 다. 4.x 는 Boot 4 기준.
    implementation("org.redisson:redisson-spring-boot-starter:3.52.0")

    // --- Lombok (@Getter, @RequiredArgsConstructor, @Builder 까지만 쓴다) ---
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    // 왜: @ConfigurationProperties 클래스에서 메타데이터를 만들어 IDE 가 application.yml 의 muq.* 를 자동완성·검증하게 한다.
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // --- 테스트 ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // 왜: @ServiceConnection 으로 컨테이너 접속 정보를 스프링 설정에 자동 주입한다.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
