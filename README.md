# DataPeek

Android 앱과 홈 화면 위젯에서 통신사 잔여 데이터를 보여주는 프로젝트입니다. Kotlin, Jetpack Compose, Jetpack Glance, DataStore, WorkManager를 사용합니다.

## 현재 지원 범위

- 프리티 SKT망: 공식 로그인 페이지 WebView 진입, 세션 표식 보안 저장, 전용 API WebView의 직접 navigation과 HTML fallback parser 제공
- SK텔레콤 T world: Provider와 공식 로그인 진입 구조만 제공
- 프리티 사용량 HTML 경로: `GET https://www.freet.co.kr/mypage/usage` 경로를 로그인 후 공식 페이지 이동으로 확인
- 프리티 사용량 API: 로그인 WebView와 같은 Android WebView Cookie store를 사용하는 전용 WebView에서 `GET https://api.freet.co.kr/mypage/usage`를 top-level navigation으로 열고 body를 JSON parser에 전달
- 프리티 일반 아이디 로그인: 기존 공식 WebView 방식 유지
- 프리티 Google 로그인: 관찰된 Google OAuth를 Custom Tab에서 처리하고 Freet HTTPS callback을 기존 WebView로 handoff
- 기기 NetworkStatsManager 추정치: 사용하지 않음

프리티 공개 페이지는 `https://www.freet.co.kr/main`, 로그인 페이지는 `https://www.freet.co.kr/login`입니다. 로그인 후 사이트의 `자세히보기`를 눌러 `https://www.freet.co.kr/mypage/usage`에 도달했고, 실제 화면에서 `MVNO Data 20GB 공제 (LTE)`, `20G`, `19.02G`, `999.81M`, `잔여일 : 2일`을 확인했습니다. 이 값은 민감정보를 포함하지 않는 익명화 fixture로 parser 테스트에 사용했습니다.

Home 수동/foreground 갱신은 로그인 직후 성공한 전용 API WebView 경로를 우선 사용하고, API 응답·body·transport가 실패할 때 새 HTML WebView를 fallback으로 사용합니다. WorkManager는 Android 백그라운드에서 headless WebView나 별도 쿠키 복사 경로를 실행하지 않고 갱신 필요 상태만 기록합니다. 실제 서버 갱신은 다음 앱 foreground 또는 Widget 상호작용에서 동일한 전용 API WebView 경로로 수행됩니다.

## 구조

- `data/model`: 공통 잔여량 모델과 오류 모델
- `data/provider`: 통신사별 Provider, MockProvider
- `data/provider/freet/FreetHtmlUsageParser.kt`: 관측된 사용량 페이지 HTML의 표와 잔여일을 파싱하는 parser
- `data/provider/freet/FreetUsageParser.kt`: 익명화된 JSON fixture를 파싱하는 parser
- `data/provider/freet/FreetDebugImport.kt`: Copy as cURL 입력에서 공식 경로와 method만 요약하며 Cookie, Authorization, token, body를 저장하지 않음
- `ui/FreetLoginNavigationPolicy.kt`: 실제 관찰된 Google OAuth navigation 차단 및 공식 도메인 allowlist 정책
- `data/repository`: 마지막 성공 snapshot과 오류 상태를 DataStore에 저장
- `data/session`: Android Keystore AES-GCM 세션 표식과 WebView 쿠키 삭제
- `ui`: Compose 화면, WebView 로그인, 설정
- `widget`: Glance 위젯(작은/중간/큰 크기 분기)과 수동 새로고침
- `worker`: 네트워크 제약이 있는 WorkManager 주기 예약 및 foreground 보충 신호 기록

새로고침 실패 시 기존 snapshot은 지우지 않습니다. 로그인 비밀번호는 앱 TextField나 저장소에 존재하지 않으며, 세션 표식은 백업에서 제외됩니다.

## 빌드

Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat lint
```

Debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

`local.properties`는 로컬 Android SDK 경로를 담으며 Git에 추가하지 않습니다.

## 프리티 사용량 API 확인 상태

현재 앱의 API-first POC와 Home refresh coordinator는 안정적인 Freet 세션에서 로그인 WebView와 별도의 전용 WebView를 생성합니다. 전용 WebView가 `https://api.freet.co.kr/mypage/usage`를 직접 top-level navigation하고, `onPageFinished` 이후 `document.body.innerText`만 읽어 body를 메모리에서 JSON parser에 전달합니다. API가 명확히 실패하거나 body를 읽지 못하면 같은 요청을 새 HTML WebView로 전환해 기존 HTML 사용량 parser를 fallback으로 실행합니다.

전용 WebView는 화면에 표시하지 않는 1dp 영역에 attach되어 응답 처리가 끝난 뒤 `stopLoading`/`clearHistory`/`destroy`로 정리합니다. Cookie 값은 읽거나 출력하거나 OkHttp에 주입하지 않으며, Android WebView의 공유 Cookie store만 사용합니다. third-party cookie 허용을 강제하지 않습니다.

Chrome CDP로 확인한 endpoint는 `GET https://api.freet.co.kr/mypage/usage`이며 XHR `200 application/json` 응답이 관찰되었습니다. 사용자 제공 Galaxy 결과에서도 같은 전용 API WebView의 HTTP 200, body 수신, JSON 파싱과 실제 사용량 표시가 성공한 것으로 확인됐습니다. 이 문서의 로컬 빌드 검증과 사용자의 실기기 검증은 별도로 구분합니다.

CDP로 확인한 로그인 흐름은 `GET /login` `200 text/html` → Google OAuth authorization document navigation → Google `/v3/signin/identifier` `200 text/html`입니다. Google authorization 요청의 query key는 `client_id`, `redirect_uri`, `response_type`, `scope`, `state`였고, 실제 `redirect_uri`는 `https://www.freet.co.kr/oauth/google`로 파싱되었습니다. 값 자체는 수집하지 않았습니다. 실제 Fetch/XHR endpoint, method, payload, CSRF/header 구조를 확정하려면 민감정보를 제거한 HAR 또는 Copy as cURL과 response body가 추가로 필요합니다. Cookie, Authorization, 전화번호, 토큰은 보내지 않습니다.

## Google 로그인 처리 상태

공식 로그인 페이지에서 실제로 관찰된 Google authorization navigation은 `accounts.google.com/o/oauth2/v2/auth`이며, 앱은 관찰된 URL을 그대로 Custom Tab에 전달합니다. Google 인증 완료 뒤 `https://www.freet.co.kr/oauth/google` callback이 DataPeek Activity로 전달되고, 기존 Freet WebView가 callback을 처리하는 흐름은 사용자가 Galaxy에서 성공으로 확인했습니다. client id, state, code, Cookie 값은 기록하지 않습니다.

사용자 제공 Galaxy 결과 기준으로 Custom Tab 로그인, Freet callback 수신, 기존 WebView handoff, Freet session 생성, 전용 API WebView 사용량 조회와 Home 표시가 성공했습니다. Chrome Cookie 추출·복사, WebView user-agent 위장, SSL 우회는 사용하지 않습니다. 일반 프리티 아이디 로그인과 공식 도메인 내 일반 navigation도 계속 지원합니다.

## 위젯과 자동 갱신

위젯은 Android App Widget receiver와 metadata를 포함하며 Glance 크기에 따라 핵심/중간/상세 정보를 나눕니다. 위젯 전체를 누르면 앱이 열리고, 새로고침을 누르면 앱이 열려 기존 `FreetUsageRefreshCoordinator`로 즉시 갱신합니다. 자동 주기는 30분, 1시간, 3시간, 6시간, 수동을 지원합니다. WorkManager 특성상 정확한 시각 실행은 보장하지 않으며, 앱이 열리지 않은 상태에서 Freet WebView가 실제 서버 값을 갱신하는 동작은 아직 Galaxy E2E로 검증하지 않았습니다.

## 테스트

단위 테스트는 Byte formatter, percent clamp, reset D-day, Home 상태, Widget 상태, Freet JSON/HTML parser의 정상/누락/잘못된 숫자/로그인 HTML, 민감정보 비노출 cURL 요약을 다룹니다. 실제 통신사 로그인 세션으로 Android 앱이 페이지를 GET하는 동작과 실제 Galaxy One UI 위젯 추가·리사이즈는 Android 기기에서 별도로 확인해야 합니다.
