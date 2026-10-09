# 처음 시작하기 (NANO용 체크리스트)

## 1. 저장소 만들기

```bash
# GitHub에서 NANOliberty/muq-server 빈 레포 생성 후
cd muq-server
git init
git remote add origin git@github.com:NANOliberty/muq-server.git
```

## 2. 커밋이 내 이름으로 남게 하기

Claude Code는 이 저장소에서 커밋을 하지 않는다 (CLAUDE.md 규칙 + `.claude/settings.json`에서 `git commit`은 항상 물어보게 설정). 커밋은 항상 NANO가 직접 한다.

그래도 혹시 Claude가 커밋하게 되는 상황에 대비해 꼬리말은 꺼두었다 (`attribution.commit: ""`). 사용자 전역에도 같은 설정을 넣어두면 다른 프로젝트에서도 적용된다:

```bash
# ~/.claude/settings.json
{
  "attribution": { "commit": "", "pr": "" }
}
```

git 사용자 정보 확인:
```bash
git config user.name
git config user.email   # GitHub 계정 이메일과 같아야 잔디가 찍힌다
```

## 3. 첫 커밋

```bash
git add CLAUDE.md .claude .gitignore .env.example README.md docker-compose.yml docker docs .github
git commit -m "chore: 프로젝트 규칙·기획서·개발 환경 초기화"
git push -u origin main
```

## 4. Claude Code 시작

```bash
claude
```

첫 메시지 예시:

> CLAUDE.md와 docs/plan.md를 읽고, M0 단계로 Spring Boot 프로젝트 스캐폴딩을 설계해줘. 코드는 아직 쓰지 말고 build.gradle.kts 의존성 목록과 패키지 뼈대, application.yml 구조만 먼저 보여줘.

Plan 모드(`Shift+Tab` 두 번)로 시작하면 설계 승인 전에 파일을 안 건드린다.

## 5. 매 기능마다 반복할 루프

1. `/explain-flow` 또는 "설계 먼저" → 흐름 설명 듣고 승인
2. 레이어 하나씩 구현 (Controller → Service → Repository → Test)
3. 테스트 통과 확인: `./gradlew test --tests '*Room*'`
4. `/learning-note` → `docs/learning/NN-*.md` 생성 → **직접 따라 친다**
5. `/commit-draft` → 메시지 복사 → **직접** `git add` / `git commit`

## 6. 디자인은 언제?

M1이 끝나고 웹 클라이언트를 붙일 때 정한다. 그 전까지 프론트는 큐가 보이고 곡 추가·투표가 되는 수준이면 충분하다. 백엔드 API가 안정된 뒤에 디자인을 잡아야 화면 때문에 API를 다시 뜯는 일이 없다.
