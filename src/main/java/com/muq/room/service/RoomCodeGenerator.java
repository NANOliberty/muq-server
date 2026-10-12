package com.muq.room.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * 방 입장 코드(6자리)를 만든다.
 * 중복 여부는 여기서 확인하지 않는다. 중복을 막는 것은 DB 유니크 제약이고, 겹치면 RoomService 가 다시 뽑는다.
 */
@Component
public class RoomCodeGenerator {

    // 왜 31종: 사람이 보고 입력하는 값이라 헷갈리는 0·O, 1·I·L 을 뺐다. 31^6 은 약 8.9억 가지다.
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";

    // 왜 상수: 길이 6은 기획 규칙이고 room.code 컬럼 길이(varchar(6))와 묶여 있다. 설정으로 바꿀 수 있는 값이 아니다.
    private static final int CODE_LENGTH = 6;

    // 왜 SecureRandom: 코드를 아는 것이 곧 입장 권한이다. 다음 코드를 예측할 수 없어야 한다.
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = secureRandom.nextInt(ALPHABET.length());
            code.append(ALPHABET.charAt(index));
        }
        return code.toString();
    }
}
