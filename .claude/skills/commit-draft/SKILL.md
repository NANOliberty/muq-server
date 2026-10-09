---
name: commit-draft
description: 현재 변경사항을 보고 커밋 메시지 초안을 제안한다. 실제 커밋은 하지 않는다. "커밋 메시지 써줘", "/commit-draft", 작업이 끝났을 때 사용.
---

# 커밋 메시지 초안

NANO가 **직접** 커밋하기 위한 메시지 초안을 만든다. `git add`, `git commit`, `git push`는 절대 실행하지 않는다.

## 절차

1. `git status` 와 `git diff` (스테이징 안 된 것 포함) 로 변경 내용을 읽는다.
2. 변경이 서로 다른 목적 2개 이상이면, **커밋을 나누라고 제안**하고 각각의 메시지를 따로 쓴다. 예: "리팩토링 + 새 기능"은 2개.
3. CLAUDE.md 의 컨벤션대로 작성한다.
4. 아래 형식으로 답하고 끝낸다. 실행 명령은 NANO가 복사해서 직접 친다.

## 답변 형식

```
### 커밋 1

git add src/main/java/com/muq/room/service/RoomQueueService.java \
        src/test/java/com/muq/room/service/RoomQueueServiceTest.java

feat(room): 큐 추가 시 중복 곡은 투표로 전환

같은 trackId가 큐에 있으면 ZADD 대신 투표 처리.
방 단위 Redisson 락으로 동시 추가 레이스 방지.
```

- 제목은 50자 이내, 한국어, 명령형 어미("~한다"가 아니라 "~전환", "~추가").
- 본문은 "무엇을" 보다 "왜". 자명하면 생략.
- Co-Authored-By, Generated with 같은 꼬리말을 **넣지 않는다**.
- 커밋에 들어가면 안 되는 파일(`.env`, `application-local.yml`, IDE 설정)이 변경에 섞여 있으면 먼저 경고한다.
