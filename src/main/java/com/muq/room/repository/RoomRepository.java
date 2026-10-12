package com.muq.room.repository;

import com.muq.room.domain.Room;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Room 의 PostgreSQL 저장소.
 * 구현체는 Spring Data JPA 가 실행 시점에 만든다. 조회 메서드는 필요한 기능이 생길 때 추가한다.
 * 여기서는 "코드로 찾기"만 한다. 열려 있는 방인지 판단하는 것은 Room.isOpen() 과 서비스의 몫이다.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {

    // 메서드 이름에서 "where code = ?" 쿼리가 만들어진다. room.code 의 유니크 인덱스(uk_room_code)를 탄다.
    Optional<Room> findByCode(String code);
}
