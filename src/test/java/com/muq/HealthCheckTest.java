package com.muq;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.muq.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * M0 완료 기준(health 200)을 검증한다.
 * 서버가 뜨는 것뿐 아니라 PostgreSQL·Redis 에 실제로 연결됐는지까지 확인한다.
 */
class HealthCheckTest extends IntegrationTestSupport {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("헬스 체크는 200이고 DB와 Redis가 모두 UP이다")
    void health() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DocumentContext json = JsonPath.parse(response.getBody());
        assertThat(json.read("$.status", String.class)).isEqualTo("UP");
        assertThat(json.read("$.components.db.status", String.class)).isEqualTo("UP");
        assertThat(json.read("$.components.redis.status", String.class)).isEqualTo("UP");
    }
}
