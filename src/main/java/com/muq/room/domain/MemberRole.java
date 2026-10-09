package com.muq.room.domain;

/**
 * 방 안에서 참여자가 갖는 권한.
 * 로그인 여부와는 별개다. 익명 세션으로 방을 만든 사람도 HOST 다.
 */
public enum MemberRole {

    /** 방을 만든 사람. 재생 제어(스킵·잠금)를 할 수 있다. */
    HOST,

    /** 코드로 입장한 사람. 곡 추가·투표·리액션을 할 수 있다. */
    GUEST
}
