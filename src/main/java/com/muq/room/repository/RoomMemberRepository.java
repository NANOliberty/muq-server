package com.muq.room.repository;

import com.muq.room.domain.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * RoomMember 의 PostgreSQL 저장소.
 * Room 과 저장소를 나눈 이유: 멤버는 방과 따로 추가·조회되고, 방을 읽을 때마다 멤버 전체를 끌고 오지 않기 위해서다.
 */
public interface RoomMemberRepository extends JpaRepository<RoomMember, Long> {
}
