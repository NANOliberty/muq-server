package com.muq.room.domain;

/**
 * 방의 수명 상태.
 * 만료 시각이 지났는지는 expiresAt 으로 판단하고, 여기에는 명시적으로 바뀐 상태만 둔다.
 */
public enum RoomStatus {

    /** 입장과 큐 변경이 가능하다. */
    ACTIVE,

    /** 호스트가 닫았거나 만료 처리됐다. 더 이상 입장할 수 없다. */
    CLOSED
}
