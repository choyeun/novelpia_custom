# Novelpia Custom

**노벨피아 비공식 Android WebView 앱**

[![GitHub downloads](https://img.shields.io/github/downloads/choyeun/novelpia_custom/total.svg?logo=github)](https://github.com/choyeun/novelpia_custom/releases)

> 노벨피아 공식 앱의 느린 로딩과 불편한 UX를 개선한 커스텀 WebView 래퍼입니다.

---

## 주요 기능

### 📱 WebView 브라우징
- **5개 WebView 인스턴스** — 메인/검색/뷰어/서재/소설상세 각각 독립 실행
- **하단 내비게이션** — 메인 · 랭킹 · 검색 · 서재 · 설정
- **서재 롱클릭** → 선호작 바로가기
- **볼륨키 페이지 이동** — 뷰어에서 볼륨 Up/Down으로 이전/다음 화면
- **링크 복사** — 뷰어/소설상세에서 롱클릭 시 URL 복사
- **주소로 이동** — 바로가기 버튼으로 URL 입력 이동
- **마지막 읽은 소설** — 다이얼로그에서 바로 이동
- **앱 상태 복원** — 종료 후 재실행 시 이전 화면 복원

### ⚡ 성능 최적화
- **리소스 디스크 캐싱** — 이미지/CSS/JS를 50MB LRU 캐시에 저장 (24h TTL)
- **페이지 로딩 표시기** — 상단 ProgressBar + "📖 로딩 중..." 토스트

### 📊 읽은 기록 자동 수집
- **localStorage 후킹** — `page_mark` / `userLastNovelData` 변경 시 자동 캡처
- **XHR/fetch API 후킹** — `/proc/` API 응답 자동 수집
- **mybook HTML 파싱** — 최근기록(최대 200개) 전체 페이지 순회
- **NativeBridge** — JavaScript → Android 네이티브 브릿지로 서버 전송
- **오프라인 큐** — Tailscale 끊겨도 SharedPreferences에 데이터 저장 후 재전송
- **서버** — `receiver.py` (port 8765)로 POST 수신 → JSON 파일 저장 + 작품 정보 자동 캐싱

### 🔄 자동 업데이트
- **백그라운드 다운로드** — GitHub Releases API로 새 버전 감지 → 자동 APK 다운로드
- **알림** — 다운로드 완료 시 상태바 알림 → 탭하면 설치
- **주기 리마인더** — 뷰어가 아닐 때 30초마다 "📲 업데이트 설치 가능" 토스트
- **설정 메뉴** — APK 이미 있으면 즉시 설치, 없으면 GitHub 확인 후 다운로드

### ⚙️ 설정
- 현재 페이지 새로고침
- 캐시 초기화 (50MB)
- 업데이트 확인
- 앱 정보

---

## 다운로드

[GitHub Releases](https://github.com/choyeun/novelpia_custom/releases)에서 최신 APK를 다운로드하세요.

설치 전 `설정 → 보안 → 알 수 없는 앱 설치 허용`이 필요할 수 있습니다.

---

## 기술 스택

| 항목 | 내용 |
|------|------|
| 언어 | Java 8 |
| 최소 SDK | Android 7.0 (API 24) |
| 타겟 SDK | Android 14 (API 34) |
| WebView | 시스템 WebView (Chrome 기반) |
| UI | Material Design (BottomNavigationView, MaterialAlertDialog) |
| 캐싱 | LRU 디스크 캐시 (50MB, SHA-256 인덱스) |
| 업데이트 | GitHub Releases API |
| CI/CD | GitHub Actions (build + test + release) |

---

## 빌드

```bash
git clone https://github.com/choyeun/novelpia_custom.git
cd novelpia_custom
./gradlew assembleRelease
```

APK는 `app/build/outputs/apk/release/`에 생성됩니다.

---

## 라이선스 및 면책 조항

이 프로젝트는 **개인적인 학습 및 편의 목적**으로 제작되었습니다.

- 노벨피아와 어떠한 공식적인 관계도 없습니다.
- 노벨피아의 공개된 웹 인터페이스를 기반으로 WebView로 구현한 프로젝트입니다.
- [노벨피아 사이트 이용약관](https://novelpia.com/page/terms_of_use)을 위반하지 않도록 사용하는 것을 전제로 합니다.
- 문제가 될 소지가 있다고 판단될 경우 비공개 전환 및 삭제될 수 있습니다.