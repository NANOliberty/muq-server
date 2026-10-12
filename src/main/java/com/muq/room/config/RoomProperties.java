package com.muq.room.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yml 의 muq.room.* 값을 타입이 있는 객체로 받는다.
 * @Value 를 여러 클래스에 흩는 대신 한 곳에 모으고, 값이 빠지거나 잘못되면 서버가 뜰 때 바로 실패하게 한다.
 */
@Validated
@ConfigurationProperties(prefix = "muq.room")
public record RoomProperties(
        @NotNull Duration ttl,
        @Min(1) int codeMaxAttempts) {
}
