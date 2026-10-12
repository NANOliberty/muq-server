package com.muq.room.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 방 코드의 형식을 검증한다.
 * 스프링 없이 객체를 직접 만들어 쓰는 순수 단위 테스트라서 컨테이너도 컨텍스트도 뜨지 않는다.
 */
class RoomCodeGeneratorTest {

    private static final int SAMPLES = 1_000;

    private final RoomCodeGenerator generator = new RoomCodeGenerator();

    @Test
    @DisplayName("코드는 6자리이고 헷갈리는 문자(0, O, 1, I, L)를 쓰지 않는다")
    void format() {
        for (int i = 0; i < SAMPLES; i++) {
            assertThat(generator.generate()).matches("[2-9A-HJKMNP-Z]{6}");
        }
    }

    @Test
    @DisplayName("코드는 매번 달라진다")
    void random() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < SAMPLES; i++) {
            codes.add(generator.generate());
        }

        // 왜 990: 8.9억 가지 중 1000개를 뽑으면 겹칠 확률은 0.1% 미만이다. 고정값을 반환하는 버그만 잡으면 된다.
        assertThat(codes).hasSizeGreaterThan(990);
    }
}
