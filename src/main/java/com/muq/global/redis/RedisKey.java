package com.muq.global.redis;

/**
 * Redis 키를 만드는 유일한 곳.
 * 키 문자열을 코드 곳곳에 흩뿌리면 오타 하나로 서로 다른 키를 읽고 쓰게 된다. 형식은 docs/plan.md 6장의 표와 같게 유지한다.
 */
public final class RedisKey {

    private RedisKey() {
    }

    /** 게스트 세션. HASH + TTL. */
    public static String guestSession(String token) {
        return "guest:" + token;
    }
}
