---
name: explain-flow
description: 특정 API 요청이나 WebSocket 메시지가 코드 안에서 어떤 순서로 흐르는지, 어떤 클래스가 왜 관여하는지 설명한다. "이 요청 흐름 설명해줘", "/explain-flow POST /rooms", "이게 어떻게 동작하는 거야" 같은 질문에 사용. 코드는 수정하지 않는다.
---

# 요청 흐름 설명

코드를 바꾸지 않고 **읽기만** 해서 설명한다. 인자로 엔드포인트, 클래스명, 또는 기능 이름이 온다.

## 절차

1. 인자로 받은 진입점을 찾는다. (Controller 메서드, `@MessageMapping`, 스케줄러, 이벤트 리스너 등)
2. 진입점에서 출발해 호출 체인을 끝까지 따라간다. Grep으로 실제 호출을 확인하고, 추측으로 쓰지 않는다.
3. 아래 형식으로 답한다. 설명은 NANO가 iOS 개발자라는 점을 감안해, 필요하면 iOS 개념에 빗댄다 (예: `@Transactional` ≈ Core Data 컨텍스트 save 단위, Bean ≈ DI 컨테이너가 관리하는 싱글턴).

## 답변 형식

**한 줄 요약**: 이 요청은 ___에서 시작해 ___를 거쳐 ___로 끝난다.

**흐름** (mermaid sequenceDiagram, 참여자 6개 이하):

```mermaid
sequenceDiagram
    participant C as Client
    participant Ctrl as RoomController
    ...
```

**단계별 설명**: 각 화살표마다 1~2줄. "무엇을" 보다 "왜 여기서" 에 집중.

**이 흐름에서 중요한 결정 1개**: 예) "락을 Service에서 잡는 이유는 Repository는 Redis 한 번 호출만 책임지게 하려고".

**실패 경로**: 예외가 어디서 나고, 어떤 ErrorCode로 어떤 HTTP 상태가 나가는지.

**직접 확인하는 법**: 관련 테스트 파일명 또는 curl 한 줄.

## 하지 말 것

- 파일을 수정하거나 새로 만들지 않는다.
- 호출 체인에 없는 클래스를 "참고로" 언급하지 않는다. 묻지 않은 걸 설명하면 길어진다.
- 200줄 넘기지 않는다.
