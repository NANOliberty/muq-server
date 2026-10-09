---
name: learning-note
description: 기능 하나를 완성한 뒤 NANO가 따라 치며 이해할 수 있는 학습 노트를 docs/learning/에 작성한다. "학습 노트 써줘", "/learning-note", 기능 구현이 끝났을 때 사용.
---

# 학습 노트 작성

방금 구현한 기능을 NANO가 **처음부터 혼자 다시 짤 수 있게** 정리한다. 코드를 복붙하는 문서가 아니라, 손으로 따라 치면서 "왜"를 이해하는 문서다.

## 절차

1. `docs/learning/` 에서 마지막 번호를 확인하고 다음 번호를 쓴다. 파일명 `NN-<kebab-topic>.md` (예: `03-room-queue-vote.md`).
2. 방금 작업한 파일들을 다시 읽고, 요청 하나가 들어와서 응답이 나갈 때까지 거치는 클래스를 순서대로 추린다.
3. 아래 템플릿대로 작성한다. 각 섹션은 짧게. 전체 150줄을 넘기지 않는다.
4. 작성 후 NANO에게 "따라치기 순서대로 직접 쳐보고, 막히는 부분을 말해 달라"고 한 줄 안내한다.

## 템플릿

```markdown
# NN. <기능 이름>

> 한 줄 요약: 이 기능은 ___를 ___로 해결한다.

## 1. 요청이 흐르는 길

`POST /api/v1/rooms/{code}/tracks` 를 예로:

1. `RoomController.addTrack()` — 요청 DTO 검증, 서비스 호출
2. `RoomQueueService.add()` — 분산락 획득 → 중복 확인 → 큐 저장 → 이벤트 발행
3. `RoomQueueRedisRepository.add()` — ZADD
4. `QueueEventPublisher.publish()` — Redis Pub/Sub
5. `QueueRelay.onMessage()` — STOMP /topic 으로 브로드캐스트

(실제 클래스명·메서드명으로 쓴다. 다이어그램이 도움되면 mermaid sequenceDiagram 추가)

## 2. 핵심 코드 (발췌, 전체 X)

가장 중요한 메서드 1~2개만, 각 줄 옆에 `// 왜:` 주석을 단 채로.

## 3. 따라치기 순서

빈 프로젝트에서 이 기능만 다시 짠다면 어떤 순서로 파일을 만들지. 각 단계에 "이 단계가 끝나면 확인할 수 있는 것"을 적는다.

1. `dto/AddTrackRequest.java` 만들기 → 컴파일만 되면 OK
2. `RoomQueueRedisRepository` 만들기 → `RedisKeyTest` 통과
3. ...

## 4. 왜 이렇게 했나 (다른 선택지)

| 결정 | 선택 | 안 고른 것 | 이유 |
| --- | --- | --- | --- |

ADR이 있으면 링크만.

## 5. 스스로 답해볼 질문

1. (구조에 대한 질문) 예: 왜 큐를 DB가 아니라 Redis Sorted Set에 두었나?
2. (실패 상황에 대한 질문) 예: 락 획득에 실패하면 사용자는 무엇을 보게 되나?
3. (확장에 대한 질문) 예: 서버가 2대가 되면 어디가 깨지고, 지금 설계는 왜 안 깨지나?

## 6. 확인 명령

```bash
./gradlew test --tests '*RoomQueue*'
```
```
