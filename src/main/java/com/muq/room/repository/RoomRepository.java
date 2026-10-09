package com.muq.room.repository;

import com.muq.room.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Room 의 PostgreSQL 저장소.
 * 구현체는 Spring Data JPA 가 실행 시점에 만든다. 조회 메서드는 필요한 기능이 생길 때 추가한다.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {
}
