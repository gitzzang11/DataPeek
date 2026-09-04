# DataPeek Verification Report

## Result

Implementation: PASS (OAuth/callback/API 성공 흐름 보존 및 사용자용 Home/Widget 정리)
User-reported Galaxy E2E: PASS for Google login, callback handoff, Freet session, usage API, parsing, display, and retry recovery
Tests: PASS
assembleDebug: PASS
Lint: PASS

Errors: 0
Warnings: 10 (Gradle/AndroidX/Compose/Jsoup/JSON 버전 업데이트 안내)

APK:

`C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`

- Exists: YES
- Bytes: 21,189,734
- SHA-256: `A1A61A52168023A1001533D44086F96AFFA0C2B59650EC1750C2476517D5BF9C`

## Previous POC

`CUSTOM_TAB_SESSION_NOT_SHARED`

이전 실기기 결과는 변경하거나 삭제하지 않았다. Custom Tab 브라우저 Cookie를
WebView로 복사하거나 추출하는 방식은 구현하지 않는다.

## New implemented flow

Freet WebView → Google OAuth → Custom Tab → Freet HTTPS callback → Android callback dispatch → DataPeek → existing WebView callback handoff → /mypage/usage verification

구현된 흐름:

- 기존 Freet WebView와 일반 프리티 ID 로그인은 유지한다.
- 실제 관찰된 `accounts.google.com/o/oauth2/v2/auth` navigation만 Custom Tabs로 연다.
- OAuth URL을 재구성하지 않고 관찰된 URL을 그대로 전달한다.
- `https://www.freet.co.kr/oauth/google`에 대해서만 `ACTION_VIEW`/`BROWSABLE` intent-filter를 추가했다.
- `android:autoVerify="false"`이며 도메인 검증 App Link라고 주장하지 않는다.
- Activity의 `onCreate`와 `onNewIntent`에서 같은 callback handler를 사용한다.
- HTTPS, host `www.freet.co.kr`, path `/oauth/google`만 검증하고 그 외 URI는 무시한다.
- callback query의 code/state/token 값을 읽거나 저장하거나 로그에 기록하지 않는다.
- 검증된 callback URI만 기존 살아 있는 Freet WebView에 한 번 전달한다.
- callback 처리 후 기존 WebView가 안정적인 `/main` 페이지를 완료하면 `Freet session appears established`만
  기록하고, 별도 API 전용 WebView를 생성한다.
- API 전용 WebView가 같은 Android WebView Cookie store를 사용해
  `https://api.freet.co.kr/mypage/usage`를 top-level `loadUrl`로 직접 연다. 로그인 WebView의
  navigation 중 JavaScript `fetch`/Promise에 의존하지 않는다.
- API 전용 WebView의 `onPageFinished` 뒤 `document.body.innerText`를 읽고, body는 메모리에서만
  기존 JSON parser에 전달한다. API WebView 생성/navigation/page finished/body received 상태를
  DEBUG 진단에 표시한다.
- API JSON parser가 실패하거나 응답 형식이 불명확하면 기존 `/mypage/usage` HTML parser를 fallback으로 사용한다.
- API JSON 성공 결과의 `UsageSnapshot`은 기존 Repository cache로 전달되어 Home/위젯 데이터에 사용된다.
- 기존 WebView가 없으면 디스크에 callback을 저장하지 않고
  `CALLBACK_HANDOFF_NOT_SUPPORTED`와 재시도 안내로 종료한다.
- DEBUG 진단 화면에 WebView, OAuth, Custom Tab, callback dispatch, WebView handoff,
  Freet callback, SNS/session 관찰, usage 인증/parser/API, 최종 결과를 표시한다.
- callback/usage 처리 중에는 `프리티 인증 확인 중…`을 표시하고,
  handoff 대기 만료는 인증 실패와 구분되는 `CALLBACK_HANDOFF_TIMEOUT`으로 표시한다.
- DEBUG 진단 화면에 Usage API WebView lifecycle, request/response/authenticated/JSON parser 상태를 별도로 표시한다.
- Android 앱 설정을 여는 `앱 링크 설정 열기` 버튼을 DEBUG 진단 카드에 제공한다.

## Google OAuth Full Flow

Google authentication: USER-REPORTED SUCCESS on Galaxy POC (수정 후 재검증 전)

Observed final Google redirect: 사용자가 제공한 Galaxy POC 진단에서 Google OAuth 감지와
Custom Tab 오픈, callback dispatch, URI validation, existing WebView handoff가 SUCCESS였다.

Freet callback:
`https://www.freet.co.kr/oauth/google`

After Freet callback: 사용자 제공 Galaxy POC에서 `Freet callback processed`는
IN_PROGRESS, `Freet authentication completed`는 SUCCESS, `/mypage/usage` 및 인증/parser는
IN_PROGRESS였고, 최종 결과가 AUTH_FAILED로 표시됐다. 이는 실제 인증 실패로 확정하지 않고,
상태 머신 조기 확정 문제로 분류했다.

Final page: 사용자 제공 결과에서 `/mypage/usage`는 IN_PROGRESS이며,
API-first 수정 후 최종 페이지/파서 성공은 NOT TESTED.

Freet session created: INCONCLUSIVE on Android POC
Set-Cookie observed: UNKNOWN on Android callback handoff
Additional token exchange: desktop Chrome 관찰에서 Freet callback 이후 서버 흐름은 있었으나,
DataPeek이 token/code/state를 교환하거나 재사용하지 않는다. API 요청도 기존 WebView가
자체 Cookie/session 정책으로 수행하며 Cookie를 추출하거나 OkHttp에 주입하지 않는다.
App/deep-link callback: VERIFIED ON GALAXY (사용자 제공 진단의 callback intent received/URI validated)
Custom Tabs usable for DataPeek: UNKNOWN after state-machine correction

Reason: callback dispatch, URI validation, existing WebView handoff는 사용자 제공 Galaxy
진단상 SUCCESS였지만, Freet callback/usage 처리가 IN_PROGRESS인 동안 기존 코드가
`CALLBACK_HANDOFF_AUTH_FAILED`를 조기 확정했다. 따라서 이번 수정은 실패 판정을 바로잡은 것이며,
수정 APK의 Google 로그인/사용량 성공은 아직 확인하지 않았다.

## Possible results

- `CALLBACK_HANDOFF_WORKS`
- `CALLBACK_NOT_DISPATCHED_TO_APP`
- `CALLBACK_HANDOFF_AUTH_FAILED`
- `CALLBACK_HANDOFF_TIMEOUT`
- `CALLBACK_HANDOFF_NOT_SUPPORTED`

## General Freet login support

일반 프리티 로그인 WebView와 기존 수동 로그인 완료 흐름은 지원한다.
Google/Naver/Kakao 계정에 대해 일반 ID 로그인 자격증명이 자동으로 생긴다고 가정하지 않는다.
소셜 계정 사용자는 이번 POC에서 실제 callback handoff 성공 여부를 별도로 확인해야 한다.

## Security boundaries

- User-Agent spoofing 없음
- SSL/TLS 우회 없음
- Chrome Cookie DB 접근·추출·복사 없음
- OAuth callback query 값 파싱·저장·로그 없음
- App Link domain ownership 검증 우회 없음
- Freet 공식 Android 앱 APK 분석·역공학 없음
- 비밀번호·2FA·본인인증 자동입력 없음

## Verification commands

- `gradlew.bat test`: PASS — 205 debug + 205 release unit tests, 0 failures, 0 errors
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS
- Lint errors: 0
- Lint warnings: 10

## Galaxy E2E

NOT TESTED

사용자 테스트 순서:

1. 위 APK를 Galaxy에 설치한다.
2. DataPeek에서 프리티 로그인으로 이동한다.
3. Google 로그인 버튼을 누른다.
4. Custom Tab에서 Google 로그인/본인인증을 직접 완료한다.
5. 브라우저가 Freet HTTPS callback에서 DataPeek을 열도록 선택한다.
6. DEBUG `진단 정보` 화면과 `/mypage/usage` 표시를 확인한다.
7. 진단 화면의 최종 결과를 위 다섯 가지 중 하나로 기록한다.

실기기 확인 전에는 `CALLBACK_HANDOFF_WORKS`, Google 로그인 성공, 실제 위젯 데이터 E2E를
완료로 표시하지 않는다.

## Galaxy retry/diagnostic follow-up

사용자가 제공한 실기기 결과:

- 1차: Google 로그인 후 DataPeek 복귀, 프리티 로그인 세션 미생성
- 2차: 프리티 로그인 WebView가 흰 화면으로 표시됨

이번 수정:

- `ACTION_VIEW` callback 수신 여부와 App resume을 별도 상태로 표시한다.
- callback 수신, host/path 검증, 기존 WebView 전달, Freet callback redirect,
  SNS login 도달, Freet 인증 완료/실패, HTML usage와 Usage API 인증/parser를 별도 진단한다.
- 실패 시 `프리티 로그인이 완료되지 않았습니다.`와 `다시 시도`를 표시한다.
- 재시도는 WebView loading 중지, timeout 취소, transient coordinator state 및
  callback 중복 guard 초기화, history 정리, `https://www.freet.co.kr/login` 재로드 순서로 처리한다.
- `about:blank`, 빈 visible content, main-frame navigation error, main-frame HTTP error를
  민감정보 없이 상태로 표시한다.
- callback URI를 pending 저장하지 않으며 Chrome Cookie·credential은 건드리지 않는다.
- Freet session appears established 이후 별도 API WebView에서 실제 관찰된
  `https://api.freet.co.kr/mypage/usage`를 먼저 조회한다.
- API 200 JSON parser 성공은 HTML parser 완료 여부와 무관하게 `CALLBACK_HANDOFF_WORKS`로 판정한다.
- API 401/403/auth error만 명확한 인증 실패로 판정하고, API timeout은
  `CALLBACK_HANDOFF_TIMEOUT`으로 분리한다.

이번 API-first 수정 후 Galaxy 재검증: NOT TESTED

사용자가 제공한 API 관찰: Chrome CDP에서 `GET https://api.freet.co.kr/mypage/usage`가
XHR `200 application/json`으로 확인됨. 이 값은 API endpoint 선택 근거이며, 이번 수정 APK가
Galaxy의 실제 WebView 세션에서 같은 응답을 받은 것은 아직 검증하지 않았다.

## Supplied Galaxy revalidation before this fix

이번 수정 직전 사용자가 제공한 실제 Galaxy 재검증 결과는 다음과 같다.

- OAuth callback dispatch: ✅ VERIFIED ON GALAXY
- Callback → existing WebView: ✅ VERIFIED ON GALAXY
- Freet session appears established: ✅ VERIFIED ON GALAXY
- Usage API: ❌ WAS NOT TRIGGERED
- Current timeout cause: API-first runtime trigger/wiring not executed
- Final observed state: `CALLBACK_HANDOFF_TIMEOUT`

이 결과는 OAuth/callback/session handoff 실패가 아니며 `Freet authentication failed`로 기록하지 않는다.

## Completion checklist

- Usage API runtime trigger: IMPLEMENTED
- Trigger condition: stable Freet `/main` `onPageFinished` → dedicated API WebView → direct API navigation
- WebView fetch: REMOVED FROM PRIMARY PATH
- HTML parser: FALLBACK
- Tests: PASS
- assembleDebug: PASS
- lint: PASS (0 errors)
- APK: `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- Galaxy E2E: NOT TESTED AFTER THIS FIX

### State-machine correction based on the Galaxy result

- Custom Tab opened: VERIFIED ON GALAXY (사용자 제공 진단)
- App resumed: VERIFIED ON GALAXY (사용자 제공 진단)
- ACTION_VIEW callback received: VERIFIED ON GALAXY (사용자 제공 진단)
- callback host/path validated: VERIFIED ON GALAXY (사용자 제공 진단)
- callback handed to existing WebView: VERIFIED ON GALAXY (사용자 제공 진단)
- Freet authentication after handoff: INCONCLUSIVE
- `/mypage/usage` authenticated/parser: IN PROGRESS in the supplied run; 수정 후 NOT TESTED
- Usage API request/response/authenticated/JSON parser: 수정 후 NOT TESTED
- old final `CALLBACK_HANDOFF_AUTH_FAILED`: 조기 확정으로 분류하며 실제 인증 실패로 기록하지 않음

수정 내용:

- callback handoff 후 `/main` 도달은 `Freet session appears established`로만 기록한다.
- `Freet authentication completed`는 사용량 페이지가 인증되고 parser 또는 API 결과가 SUCCESS일 때만 SUCCESS다.
- callback/usage/parser가 IN_PROGRESS이면 최종 결과를 AUTH_FAILED로 바꾸지 않는다.
- WebView 오류는 별도 `webViewIssue`로 표시하며, handoff 제한 시간이 지나면
  `CALLBACK_HANDOFF_TIMEOUT`으로 종료한다.
- `/login` 재진입, 명시적 인증 실패, usage parser/API FAIL만 AUTH_FAILED 근거로 사용한다.
- 재시도는 진행 중 로딩 중지, timeout 취소, coordinator transient state 및 중복 guard 초기화,
  history 정리 후 `https://www.freet.co.kr/login`을 새로 로드한다.

## Dedicated API WebView follow-up

### Supplied Galaxy result before this change

- Google OAuth detected: SUCCESS
- Custom Tab opened: SUCCESS
- App resumed: SUCCESS
- Callback intent received: SUCCESS
- Callback URI validated: SUCCESS
- Existing WebView alive: SUCCESS
- Callback handed to WebView: SUCCESS
- Freet session appears established: SUCCESS
- Usage API request started: SUCCESS
- Usage API response: `API_TIMEOUT`
- Usage API HTTP: NOT_TESTED
- WebView issue: NONE

이 결과는 OAuth/callback handoff 또는 프리티 인증 실패로 확정하지 않는다. 기존 로그인
WebView에서 실행한 JavaScript `fetch`의 응답 callback이 timeout된 것만 실제로 확인됐다.
redirect/navigation 중 fetch 실행 context가 소멸했을 가능성은 가설이며 확정 원인으로 기록하지 않는다.

### Change implemented

- Dedicated API WebView: IMPLEMENTED
- Direct API navigation: IMPLEMENTED — `GET https://api.freet.co.kr/mypage/usage`를
  전용 WebView의 top-level `loadUrl`로 실행
- Probe start point: IMPLEMENTED — 안정적인 Freet `/main` `onPageFinished` 이후에만 생성/시작
- Cookie handling: SHARED WEBVIEW COOKIE STORE, NO COOKIE EXTRACTION
- WebView fetch: REMOVED FROM PRIMARY PATH
- API WebView lifecycle diagnostics: IMPLEMENTED — created, navigation started, page finished,
  body received, HTTP status, JSON parser
- API body read: IMPLEMENTED — page 완료 후 `document.body.innerText`를 메모리에서 파싱
- HTML parser: FALLBACK — API 오류/비 JSON/body 읽기 실패 시 기존 `/mypage/usage` 사용
- API WebView cleanup: IMPLEMENTED — response/timeout/recomposition 종료 시 loading 중지,
  history 정리, destroy; Cookie 삭제 없음
- OAuth/callback structure: UNCHANGED

### Verification status after this change

이 문서의 전용 API WebView 구현 상태는 소스와 단위 테스트로 확인한 결과다. 수정 후 실제
Galaxy에서 Google 인증부터 API 200 JSON body 획득까지의 E2E는 아직 실행하지 않았으므로
`CALLBACK_HANDOFF_WORKS`, Google 로그인 성공, 실제 위젯 데이터 성공으로 표시하지 않는다.

- Direct API 200 JSON parser test: PASS
- Direct API login HTML/auth failure test: PASS
- API body read failure/fallback state test: PASS
- Dedicated API WebView lifecycle state test: PASS
- Galaxy E2E after this change: NOT TESTED

## Response parsing and delivery follow-up

### Supplied Galaxy evidence before this change

사용자가 제공한 실제 Galaxy 결과에서는 Google OAuth, callback handoff, Freet session,
`GET https://api.freet.co.kr/mypage/usage` HTTP 200, API body 수신, API 인증 및 DEBUG
`Usage JSON parser: SUCCESS`가 모두 SUCCESS였다. 그러나 Home에는 사용량이 표시되지 않고
`응답을 해석하지 못했습니다`가 표시됐다. 이 결과는 인증/API 실패가 아니라, 성공한
`UsageSnapshot`이 Home 경로로 전달되지 않았을 가능성을 조사하는 입력으로 사용했다.

### Actual response-body availability

현재 프로젝트의 `.tmp/oauth-network-log.json`에는 해당 API의 method/path/status와
sanitized header만 있고 response body는 저장되어 있지 않다. 따라서 실제 Galaxy 응답의
field path를 확인하지 못했으며, 실제 응답이라고 주장하는 새 fixture도 만들지 않았다.
기존 `freet_skt_usage_success.json`은 기존 sanitized parser fixture로만 취급한다.

### Change implemented

- JSON parser diagnostics: IMPLEMENTED — JSON syntax, schema match, three core usage fields,
  value validity, `UsageSnapshot` creation을 독립 상태로 기록한다.
- Strict success rule: IMPLEMENTED — JSON 문법만 맞거나 일부 값만 존재하는 응답은 성공으로
  처리하지 않으며, null/zero/negative/overflow/impossible 관계를 거부한다.
- Response structure viewer: IMPLEMENTED — DEBUG 진단의 `응답 구조 보기`에서 root/type/key/
  nested key/array length/candidate만 표시하고 body value는 표시하지 않는다. 민감 가능성이
  있는 key는 redacted 처리한다.
- API probe handoff: IMPLEMENTED — 직접 API WebView body가 실제 usable snapshot을 만들었을
  때만 API 성공으로 코디네이터에 전달한다.
- HTML fallback handoff: IMPLEMENTED — HTML parser가 만든 snapshot도 그대로 전달해 성공 뒤
  별도 재조회가 parse error를 덮어쓰지 않게 했다.
- Delivery diagnostics: IMPLEMENTED — Repository cache, Home UI, Widget update request를
  별도 상태로 기록한다.
- OAuth/WebView/session security: UNCHANGED — OAuth 흐름, API endpoint, WebView Cookie
  store 사용 방식, Chrome Cookie 접근/복사, UA/SSL 정책은 변경하지 않았다.

### Verification status

- `gradlew.bat test`: PASS
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS (0 errors)
- Debug APK: EXISTS — `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- Debug APK SHA-256: `37286F59E932E335D19EB42CDD5AFB52F031DB5E786D397EA9ED70FF5508D8A0`
- Galaxy E2E after this parser/delivery change: NOT TESTED
- Real response field mapping: UNKNOWN until a body-containing, sanitized runtime capture is
  available

## White-screen retry fix

### Root cause found in source flow

- Retry previously stopped and reloaded the existing Login WebView. It did not recreate the
  instance, so an OAuth/callback/renderer state could remain attached to the next login.
- API WebView cleanup depended on conditional Compose disposal and had no explicit cleanup
  diagnostic.
- Timeout and callback handlers were not associated with an attempt ID, and the consumed
  Activity callback intent was not replaced with a neutral intent.

### Fix status

- White screen retry bug: FIXED IN CODE — source/state tests
- Login WebView recreation: IMPLEMENTED — attempt ID is a Compose key; old WebView is stopped,
  detached, and destroyed on disposal.
- Transient OAuth reset: IMPLEMENTED — OAuth/API flags, callback guards, timeouts, POC result,
  stale error state, and usage request state reset through one retry entry point.
- API WebView cleanup: IMPLEMENTED — stop, detach, clear history/views, destroy, and cleanup
  status callback; CookieManager data is not deleted.
- Blank auto recovery: IMPLEMENTED — blank/navigation/HTTP/render-process/content failures can
  trigger one automatic new `/login` attempt; manual retry resets the recovery budget.
- Attempt isolation: IMPLEMENTED — stale WebView callbacks, stale timeout callbacks, and
  callback replay from an older attempt are ignored.
- Callback replay protection: IMPLEMENTED — consumed/invalid callback intents are replaced with
  a neutral `ACTION_MAIN` intent; callback acceptance requires an active OAuth attempt.
- Login WebView state diagnostics: IMPLEMENTED — attempt ID, phase, WebView created, page started/
  finished, recovery, retry reset, and API WebView cleaned.

### Verification

- Retry state tests: PASS
- Full `gradlew.bat test`: PASS
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS (0 errors, 10 warnings)
- APK: EXISTS — `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK SHA-256: `3BBABB63B5EE8F1349AFABF24315846E03950EFC07B8FD6409D07A5A0E9A1D80`
- Galaxy E2E after this fix: NOT TESTED

---

# DataPeek Finalization

이 절은 사용자가 제공한 실제 Galaxy E2E 결과와 이번 로컬 코드 검증을 분리해 기록한다.

## User-reported Galaxy E2E

- Freet Google login: PASS (사용자 제공 실기기 결과)
- Freet callback: PASS (사용자 제공 실기기 결과)
- Freet session: PASS (사용자 제공 실기기 결과)
- Freet usage API: PASS (사용자 제공 실기기 결과)
- Usage parsing: PASS (사용자 제공 실기기 결과)
- Actual usage displayed: PASS (사용자 제공 실기기 결과)
- Retry/white-screen recovery: PASS (사용자 제공 실기기 결과)

이 PASS 표시는 Codex가 이번 실행에서 Galaxy를 직접 조작했다는 뜻이 아니라, 사용자가 전달한 실기기 결과를 반영한 것이다.
또한 이 Galaxy 결과는 이번 사용자용 UI 정리 빌드 이전의 성공 증거이므로, 최종 APK의 Galaxy 재검증 전에는 이번 변경분까지 포함한 E2E 완료로 표시하지 않는다.

## Local implementation and verification

- Home UI: IMPLEMENTED / GALAXY VISUAL RECHECK PENDING — 실제 UsageSnapshot 중심 표시, 상태(`정상`/`로그인 필요`/`업데이트 실패`), 세션 만료 시 재로그인 경로, 마지막 성공 데이터 유지
- Widget: IMPLEMENTED / GALAXY RECHECK PENDING — 캐시된 UsageSnapshot 표시, 작은/중간/큰 레이아웃, 인증/업데이트 오류 상태, 위젯 새로고침 경로
- Release UI cleanup: PASS — POC 상태와 내부 결과명은 DEBUG 진단 화면에서만 표시하고 일반 화면에는 사용자용 문구만 표시
- Session expiry handling: IMPLEMENTED / GALAXY RECHECK NOT PERFORMED
- Automatic refresh: PARTIAL — 앱 시작/foreground 복귀와 WorkManager 경로 구현; 장기 백그라운드 세션 유지 E2E는 미검증
- Usage formatting: PASS — MB/GB를 최대 소수 둘째 자리까지 표시하고 불필요한 0은 제거
- OAuth/Custom Tab/callback structure: PRESERVED — 이번 정리에서 재설계하지 않음

## Build verification

- `gradlew.bat test`: PASS
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS (0 errors)
- Debug APK: `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK existence: VERIFIED LOCALLY
- Debug APK size: 21,189,734 bytes
- Debug APK SHA-256: `44BD9A20E174E188532D2B9CF91C722D3AC396BDF4674187C1A01FDD924EFB27`
- `gradlew.bat assembleRelease`: PASS — unsigned APK generated at `C:\src\DataPeek\app\build\outputs\apk\release\app-release-unsigned.apk`

## Remaining limitations

- WorkManager 기반 자동 갱신의 장기 세션 유지와 기기 재부팅 후 동작은 Galaxy E2E 미검증
- One UI 홈에서 위젯 추가/리사이즈와 다크 모드·큰 글꼴의 시각 검증은 별도 실기기 확인 필요
- 실제 Galaxy 결과는 사용자 제공 사실이며, 이번 로컬 Gradle 실행은 소스·단위 테스트·APK·lint 검증만 수행
- OAuth code/state/token, Cookie, 비밀번호와 같은 민감 값은 저장하거나 보고하지 않음

## Final refresh regression fix

### Actual source path comparison

- Manual Home refresh before this fix: `HomeScreen 새로고침 → UsageViewModel.refresh() → UsageRepository.refresh() → FreetSktProvider.fetchUsage() → www.freet.co.kr/mypage/usage HTML → FreetHtmlUsageParser`.
- Login POC usage path: `Freet session → dedicated API WebView → GET https://api.freet.co.kr/mypage/usage → document.body.innerText → FreetUsageParser → UsageSnapshot`.
- The reported parse-error regression was caused by these two paths being separate; Home refresh did not reuse the API WebView path that had succeeded on Galaxy.

### Change status

- Unified refresh pipeline: IMPLEMENTED for Home manual refresh, foreground refresh, and the non-POC login-complete refresh trigger.
- JSON API primary: YES — a fresh dedicated API WebView is created for each request and performs top-level API navigation.
- HTML fallback: YES — API transport/body/non-auth failures switch the same request to a fresh HTML WebView parse; the existing provider HTML path remains available for WorkManager/background execution.
- Snapshot delivery: IMPLEMENTED — successful API/HTML parse uses shared `UsageViewModel` delivery to Repository cache, Home state, and Widget update request; a failed refresh keeps the last snapshot.
- Duplicate refresh protection: IMPLEMENTED — the ViewModel disables concurrent refresh state and the request coordinator rejects a second pending request.
- Session-expired handling: IMPLEMENTED — API 401/403 or login HTML maps to session-expired UI, not the generic parse-error message.
- Parse-error handling: FIXED IN CODE — only JSON/schema or HTML usage parsing failures map to `응답을 해석하지 못했습니다.`; API request, body-read, and timeout failures have separate messages.
- OAuth/Custom Tab/callback/CookieManager/User-Agent/SSL behavior: UNCHANGED.

### Verification status

- `gradlew.bat test`: PASS
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS, 0 errors, 10 warnings
- APK: `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK existence: VERIFIED LOCALLY
- APK size: 21,189,734 bytes
- APK SHA-256: `A1A61A52168023A1001533D44086F96AFFA0C2B59650EC1750C2476517D5BF9C`
- Galaxy manual refresh E2E after this fix: NOT TESTED

The implementation and unit tests prove the local path selection and result classification. They do not prove that the final APK receives an API 200 body from the user's authenticated Galaxy WebView; that requires a new device run.

## Manual refresh timeout regression follow-up

### Manual refresh timeout root cause

확인된 코드상 원인은 Home 경로의 이전 timeout `LaunchedEffect`가 refresh request가 Compose에
표시되는 순간부터 15초를 측정한 점이다. 로그인 POC 경로는 API WebView가 실제로 navigation을
시작한 뒤 별도 lifecycle을 진행했지만, Home 경로는 WebView attach/load 이전의 scheduling과
WebView 생성 시간을 같은 API timeout에 포함했다. 따라서 같은 `api.freet.co.kr/mypage/usage`
요청이 로그인 직후에는 성공하고 Home 수동 갱신에서는 timeout으로 끝날 수 있었다. 실제
Galaxy에서 네트워크가 느렸다는 증거가 아니라, 소스 비교로 확인한 lifecycle/wiring 차이다.

### Path comparison and fix

- Login-success API path: Freet callback/session → LoginScreen의 dedicated API WebView →
  `loadUrl(https://api.freet.co.kr/mypage/usage)` → page finished → body extraction →
  `FreetUsageParser` → POC snapshot delivery.
- Manual/foreground path before fix: Home → `UsageViewModel.refresh()` → pending request가
  Compose에 표시됨 → request 시점부터 독립 timeout → dedicated API WebView path가 같은
  timeout에 종속됨.
- Manual/foreground path after fix: Home/foreground → one
  `FreetUsageRefreshCoordinator` request/lock → Activity check → 1dp API WebView created and
  attached in the real Compose hierarchy → `loadUrl` → page/body callbacks → parser → shared
  `UsageViewModel` repository/cache + Home + Widget delivery → WebView cleanup.
- API non-auth/body failures use a fresh HTML WebView fallback; 401/403 and host/lifecycle
  failures are kept as distinct failures and are not converted into a generic timeout.
- API and fallback WebView resources are keyed by refresh ID and target, so a late cleanup or
  callback from refresh A cannot destroy or complete refresh B.

### Diagnostic and concurrency status

- Refresh diagnostic stages: IMPLEMENTED — request, lock, Activity, WebView create/attach,
  navigation request, page start/finish, HTTP status, body read/start, parser, repository,
  cleanup, and final result; only status/HTTP code are shown, never Cookie/token/body values.
- Failure categories: IMPLEMENTED — host unavailable, WebView attach/create, navigation/body
  timeout, HTTP 401/403/error, parser failure, session expired, superseded/stale refresh.
- UI-thread WebView operations: ALREADY CORRECT — WebView creation, attach, `loadUrl`,
  `evaluateJavascript`, detach, and destroy remain in `AndroidView`/WebView callbacks on the
  main thread; ViewModel coroutines only submit state/results.
- Refresh race: FOUND AND FIXED — coordinator lock plus synchronized request/complete/fallback,
  refresh IDs, delivered guard, target-keyed disposal, and stale callback checks.
- Timeout start point: after the API WebView is attached and navigation is requested; request
  lock wait and Compose scheduling are excluded. Timeout duration was not increased.
- Unified coordinator: YES for Home manual refresh, foreground refresh, and non-POC login
  completion refresh. The Google OAuth/Custom Tab/callback/session/Cookie/User-Agent/SSL flow
  was not changed.

### Verification

- `gradlew.bat test`: PASS
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS — 0 errors, 10 warnings
- Focused coordinator/parser tests: PASS
- APK: EXISTS — `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK size: 21,189,734 bytes
- APK SHA-256: `C2822BF4470DFBCE8C2F1645ED29CE2A9E8B607208B2CD47923217599FE2C04A`
- Galaxy E2E after this fix: NOT TESTED — no ADB-connected Galaxy was available in this run.

## API body decode and parser-state regression follow-up

### Confirmed diagnosis

사용자가 제공한 Galaxy 진단은 HTTP 200과 body 수신까지는 성공했지만 JSON parser가 FAIL인
상태였다. 기존 구현은 `evaluateJavascript` callback 값을 JSON-encoded JavaScript string일
수 있다는 별도 계약으로 표현하지 않았고, API parser 실패 뒤 HTML fallback 성공을 일반적인
`Final refresh SUCCESS`로만 표시했다. 따라서 실제로는 fallback이 성공했을 가능성과 parser
실패 후 새 snapshot이 저장된 경우를 진단 화면에서 구분할 수 없었다.

실제 Galaxy body 원문은 이번 실행에서 새로 수집하지 않았다. 그러므로 FreetUsageParser에
새 field path를 추측해 추가하지 않았고, 아래 schema 결과를 실제 Galaxy 값으로 표시하지
않는다.

### Change status

- evaluateJavascript wrapper detection: IMPLEMENTED — callback의 JSON string wrapper를
  `JSONTokener`로 정확히 한 번 decode하며 임의의 backslash 치환을 사용하지 않는다.
- Body decoding: FIXED — decoded body만 `FreetUsageParser`에 전달하고, raw/decoded 길이와
  첫 문자만 DEBUG 진단에 보낸다.
- Decoded root type: IMPLEMENTED — OBJECT/ARRAY/STRING/HTML/UNKNOWN을 값 없이 판정한다.
  실제 Galaxy의 이번 수정 후 root type: NOT TESTED.
- Actual schema diagnostic: IMPLEMENTED — foreground/manual refresh에서도 root type, 안전하게
  redacted된 key/type/path, nested node, array length을 DEBUG에서 확인할 수 있다.
- Parser failure stage: IMPLEMENTED — callback decoded, JSON syntax, root supported, schema
  matched, usage fields found, usage values parsed, UsageSnapshot created를 분리 표시한다.
  실제 Galaxy의 세부 실패 단계: NOT TESTED until the next device run.
- Parser FAIL → refresh SUCCESS bug: FIXED — direct parser failure cannot update the repository
  or complete refresh successfully. JSON failure followed by successful HTML fallback is shown
  as `SUCCESS_FALLBACK`; failed fallback is `FAIL` with `JSON_PARSE_FAILED`.
- Cache retained on failure: YES — failed refresh does not clear or replace the previous valid
  snapshot; only a newly parsed valid snapshot is saved.
- OAuth/Custom Tab/callback/Cookie/endpoint/User-Agent/SSL: UNCHANGED.

### Regression tests and verification

- evaluateJavascript quoted JSON, escaped quotes, Unicode, OBJECT, ARRAY, and HTML detection:
  PASS
- HTTP 200 + parser FAIL cannot be a regular refresh success: PASS
- parser failure leaves repository-update stage unsuccessful: PASS
- JSON failure + HTML fallback success/failure outcomes: PASS
- `gradlew.bat test`: PASS
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS — 0 errors, 10 warnings
- APK: EXISTS — `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK size: 21,189,734 bytes
- APK SHA-256: `F25E870CBAA0E8E49D5AF9D690459637D83857B7B9F7C58EA7308A08547FA2A7`
- Galaxy E2E after this fix: NOT TESTED — ADB-connected Galaxy was unavailable.

## Confirmed Freet usage JSON schema mapping

이번 수정은 사용자가 실제 Galaxy에서 확인한 응답 구조만 기준으로 했다. OAuth, Custom Tab,
callback handoff, CookieManager, dedicated API WebView, API endpoint, refresh lifecycle,
evaluateJavascript decode, HTML fallback은 변경하지 않았다.

### Actual mapping

- Root: `OBJECT`
- Data container: `$.data` (`OBJECT`)
- Data usage entry: `$.data.DATA[0]` (`OBJECT`)만 사용
- Total: `totalText` 우선, 실패/공백 시 `total` fallback
- Used: `useText` 우선, 실패/공백 시 `use` fallback
- Remaining: `remainText` 우선, 실패/공백 시 `remain` fallback
- Remaining percent: `remainRate`가 유효하면 사용하고 0~100으로 clamp; 없거나 해석할 수
  없으면 byte 비율을 사용
- Snapshot source: `CARRIER_SERVER`

`VOICE`, `SMS`, `DETAIL.jsonResult`, `usageType`, `useDay`는 데이터 byte 계산이나 snapshot
메타데이터에 사용하지 않는다. 따라서 VOICE/SMS만 있는 응답은 데이터 사용량 schema로
인정하지 않는다. `G`/`M` 및 `GB`/`MB`의 대소문자·공백 변형은 기존 `parseDataSize`의
1024 기반을 유지해 처리한다.

### Parser and fixture changes

- `FreetUsageParser`: 임의의 중첩 field 탐색을 제거하고 확인된 `data.DATA[0]` path만 읽도록
  변경
- Text field 우선 및 raw field fallback 구현
- `remainRate`의 숫자/`%` 형식과 범위 처리 구현
- 실제 key 구조를 유지한 sanitized fixture 추가:
  `app/src/test/resources/freet_usage_api_real_shape.json`
- Parser diagnostics는 schema, fields, values, snapshot 생성 단계를 계속 분리한다.
- JSON parser가 실패하면 새 snapshot을 만들거나 Repository를 성공 갱신하지 않는다. 기존
  cache는 유지하며 HTML fallback의 기존 동작은 유지한다.

### Regression tests

- `DATA[0]`의 `20G`, `19.02G`, `999.81M` 및 `remainRate` parse: PASS
- Text 우선, raw fallback, malformed text fallback: PASS
- `DATA` 누락/empty/`DATA[0]` null: PASS
- VOICE-only/SMS-only 응답 거부: PASS
- `remainRate` `%` 형식 및 0~100 clamp: PASS
- 실제 shape fixture의 schema/fields/values/snapshot diagnostics: PASS
- API probe의 JSON success/failure 분류 회귀: PASS

### Verification after schema mapping change

- evaluateJavascript wrapper detected: ALREADY CORRECT / unchanged in this task
- Body decoding: ALREADY CORRECT / unchanged in this task
- Decoded root type: OBJECT in the sanitized real-shape fixture; Galaxy rerun: NOT TESTED
- Actual schema diagnostic: IMPLEMENTED and covered by fixture structure test
- JSON parser failure stage: schema/fields/value/snapshot diagnostics remain separated
- Parser FAIL → refresh SUCCESS bug: FIXED previously; this task did not change that state machine
- Cache retained on parser failure: YES, covered by existing regression tests
- `gradlew.bat test`: PASS
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS — 0 errors, 0 warnings in the generated lint XML
- APK: EXISTS — `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK size: 21,189,734 bytes
- APK SHA-256: `361489B001863D3EA53DCFE6ADDA1D5898FCD3AE80975CCD015A05A39A4FD76D`
- Galaxy E2E after this parser mapping change: NOT TESTED — ADB-connected Galaxy was unavailable.

## Unit conversion regression follow-up

### Unit conversion root cause

현재 체크아웃된 소스에서는 Galaxy의 `20G`가 `20MB`로 변환되는 현상이 재현되지 않았다.
변경 전 focused regression test도 `20G → 20 GB`, `19.02G → 19.02 GB`, `999.81M →
999.81 MB`를 PASS했다. 따라서 특정 runtime 원인을 확인하지 못했으며, stale APK나 다른
runtime 상태라는 추측을 사실로 기록하지 않는다.

다만 기존 `parseDataSize`는 짧은 suffix와 긴 suffix가 섞인 단일 optional regex 및
암묵적인 bytes fallback branch를 사용하고 있었다. 이번 수정에서는 이 모호성을 제거하고
suffix를 명시적으로 판정하도록 hardened했다. 이것이 현재 소스에서 확인 가능한 실제
변경 근거이며, Galaxy에서 발생한 원인의 확정 판정은 다음 실기기 재현이 필요하다.

### Conversion implementation

- `G` / `GB` / `GIB` → `1024³` bytes
- `M` / `MB` / `MIB` → `1024²` bytes
- `K` / `KB` / `KIB` → `1024` bytes
- `B` / `byte` / `bytes` → bytes; suffix 없음은 fallback unit이 있을 때만 해당 단위 사용
- 긴 suffix를 우선하는 명시적 regex와 BigDecimal 계산 사용
- `FreetUsageParser` text/raw 경로 모두 동일한 `parseDataSize` 사용
- `formatBytes`도 동일한 1024 기반 상수로 표시
- OAuth, API, schema mapping, refresh, HTML fallback은 변경하지 않음

### Regression results

- `20G` / `20GB` / `20 GB` / `20g` / ` 20G `: PASS
- `19.02G` / `19.02 G` → `19.02 GB`: PASS
- `999.81M` → `999.81 MB`: PASS
- `999MB` / `999 MB` / `999m`: PASS
- `20G > 999.81M`: PASS
- `1G == 1024M`: PASS
- Parser → formatter round trip: PASS
- `20G`가 `20MB`가 되지 않는 회귀: PASS

### Verification after unit conversion change

- Unit conversion root cause: NOT CONFIRMED in current source; explicit parser hardening applied
- G/GB parsing: FIXED / PASS
- M/MB parsing: PASS
- 20G regression: PASS
- 19.02G regression: PASS
- 999.81M regression: PASS
- Parser → formatter round trip: PASS
- `gradlew.bat test`: PASS
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS — 0 errors, 0 warnings in generated lint XML
- APK: EXISTS — `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK size: 21,189,734 bytes
- APK SHA-256: `84265267262A42564955B5B284D42288CE56118141982FE3814AE0F398B2D06`
- Galaxy E2E after unit conversion change: NOT TESTED — ADB-connected Galaxy was unavailable.

## Unitless DATA[0] value correction

### Supplied Galaxy evidence

사용자가 제공한 Galaxy 화면에는 `전체 20 GB`, `사용 0.3 MB`, `남음 19.7 MB`, 게이지 약
1%가 표시됐다. 이 실행에서는 해당 화면의 원문 `DATA[0]` 값을 직접 새로 캡처하지 않았으므로
다음 raw 항목의 실제 Galaxy 값은 모두 `NOT CAPTURED`로 기록한다.

- `total`: NOT CAPTURED
- `totalText`: NOT CAPTURED
- `use`: NOT CAPTURED
- `useText`: NOT CAPTURED
- `remain`: NOT CAPTURED
- `remainText`: NOT CAPTURED
- `remainRate`: NOT CAPTURED
- `dataType`: NOT CAPTURED

DEBUG 진단 화면에는 이제 위의 확인된 DATA[0] 8개 항목만 표시되며, `fallbackUnit`과 각
필드의 resolved unit도 함께 표시한다. Cookie, token, 계정 식별자, 전화번호 및 raw JSON은
표시하지 않는다.

### Root cause confirmed from code path

- `FreetUsageParser`는 단위 없는 `useText`/`remainText`를 `DATA[0].dataType`으로 보완하지
  않고 공통 parser에 전달했다. 따라서 `dataType = G`인 경우 `0.3`, `19.7`을 G로 해석할
  경로가 없었다.
- bytes로 변환된 뒤에도 `UsageSnapshot`에 필드별 원래 단위가 없어, adaptive formatter가
  1 GiB 미만 값을 MB로 표시할 수 있었다. 이로 인해 `0.3 GiB`가 `307.2 MB`처럼 단위가
  바뀌는 표시 손실이 생길 수 있었다.
- 위 원인은 소스 경로에서 확인했지만, 사용자가 제공한 Galaxy 화면의 raw DATA[0] 값으로
  재현한 것은 아니므로 해당 기기에서의 최종 runtime 원인 판정은 `NOT TESTED`다.

### Fix

- `parseDataSize(value, fallbackUnit)` 및 `parseDataSizeWithUnit` 추가
- 값 자체의 suffix(`G/GB`, `M/MB`, `K/KB`, `B`)가 항상 우선
- suffix가 없을 때만 `DATA[0].dataType`을 `G/GB`, `M/MB`, `K/KB`로 normalize해 fallback
- suffix와 fallback이 모두 없으면 실패; 단위 없는 값을 임의로 MB로 처리하지 않음
- BigDecimal과 기존 1024 기반으로 bytes 계산
- `UsageSnapshot`에 total/used/remaining unit을 보존하고 DataStore에도 저장
- Home/Widget formatter가 해당 필드 unit을 사용해 `0.3 GB`, `19.7 GB`를 표시
- OAuth, API endpoint, wrapper decode, DATA[0] schema path, refresh, HTML fallback은 변경하지 않음

### Diagnostic and regression results

- Confirmed unit source: VALUE_SUFFIX 우선, 없으면 DATA_TYPE fallback
- `dataType = G`, `useText = 0.3`, `remainText = 19.7`: PASS in unit/parser tests
- `dataType = G`, `useText = 0.3G`: explicit suffix wins, PASS
- `dataType = M`, `remainText = 999.81`: `999.81 MB`, PASS
- `DATA[0]` values visible in DEBUG diagnostics: IMPLEMENTED
- Unit sanity warning: not added; server 합계 강제 검증은 하지 않음
- `20G` regression: PASS
- `19.02G` regression: PASS
- `999.81M` regression: PASS
- `20G`가 `20MB`로 표시되지 않는 회귀: PASS
- Parser → formatter round trip: PASS
- Cache unit persistence: IMPLEMENTED in `UsageCacheStore`
- `gradlew.bat test`: PASS
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS — 0 errors, 0 warnings in generated lint XML
- APK: EXISTS — `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK size: 21,189,734 bytes
- APK SHA-256: `531D4EFADB81049B1631272FBBB795F92A99EE6F0BFC0418BD2E7B1BEAFFCDEB`
- Galaxy E2E after this unitless DATA[0] fix: NOT TESTED — ADB-connected Galaxy was unavailable.

## Confirmed Freet DATA raw KiB and compound text correction

### Raw evidence supplied from Galaxy

이번 수정은 사용자가 실제 Galaxy에서 확보한 `DATA[0]` 값을 기준으로 했다.

```text
total: 20971520
totalText: 20G
use: 315733
useText: 308M 341K
remain: 20655787
remainText: 19G 715M
remainRate: 1
dataType: DATA_BASIC_1
```

- Raw Freet DATA unit: `KiB`
- `20971520 KiB * 1024 = 20 GiB`
- `315733 KiB = 308 MiB + 341 KiB`
- `315733 + 20655787 = 20971520`
- `dataType = DATA_BASIC_1`: a service/data type, **not a unit**
- `remainRate = 1`: its semantic meaning is not assumed; the gauge now derives remaining percentage from raw bytes

### Root cause confirmed from source

`FreetUsageParser` was parsing the display text first and deriving a fallback unit from
`dataType`. This was incompatible with the confirmed response: the raw numeric fields are
exact KiB values, while `useText` and `remainText` can be compound display strings. The
snapshot also retained parser source units, which could force a sub-1-GiB value into the
wrong display unit. The source-level root cause is now fixed; post-fix Galaxy runtime
verification remains pending.

### Implementation

- Raw `total/use/remain` numeric values are parsed first as KiB and converted with `bytes = KiB * 1024`.
- Conversion uses `BigDecimal` and rejects values that overflow `Long`.
- `totalText/useText/remainText` are fallback only when the corresponding raw field is missing or invalid.
- Compound text parser supports `G/GB`, `M/MB`, `K/KB`, `B` tokens, including `308M 341K` and `19G 715M`.
- `dataType` is retained only for safe DEBUG diagnostics and is never passed as a unit fallback.
- Freet snapshots use adaptive formatting: `< 1 GiB` as MB and `>= 1 GiB` as GB.
- Gauge percentage is derived from `remainingBytes / totalBytes`; `remainRate` is not used.
- The DEBUG DATA[0] panel shows only the permitted usage fields and parsed source units; no cookie, token, account identifier, phone number, or raw JSON is shown.
- OAuth, Custom Tab, callback handoff, Freet session, API WebView, endpoint, wrapper decode, refresh pipeline, and HTML fallback were not changed.

### Expected confirmed-fixture display

- Total: `20 GB`
- Used: `308.33 MB`
- Remaining: `19.7 GB`

### Regression and API probe results

- `20971520 KiB -> 20 GB`: PASS
- `315733 KiB -> 308.33 MB`: PASS
- `20655787 KiB -> 19.7 GB`: PASS
- Raw sum invariant: PASS
- `308M 341K` compound parse: PASS
- `19G 715M` compound parse: PASS
- Raw fields take priority over conflicting text: PASS
- Text fallback when raw is invalid/missing: PASS
- `DATA_BASIC_1` is not a unit fallback: PASS
- `remainRate=1` is ignored for gauge calculation: PASS
- Direct API probe with the confirmed fixture: PASS
- JSON parser / snapshot creation: PASS

### Verification after raw-unit correction

- `gradlew.bat test`: PASS
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS — 0 errors; 10 dependency/tooling update warnings reported by lint
- APK: EXISTS — `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK size: 21,189,734 bytes
- APK SHA-256: `9D36C097B547D8DC4ACA5E395E757450713FFE6FF1F7AB58BB4B3EB4A440DA3D`
- Galaxy E2E after raw-unit correction: NOT TESTED

## Hidden developer mode for development tools

### User-facing behavior

- 일반 Home 화면에서 `Mock 보기` 진입점을 제거했다.
- 일반 설정 화면에서 OAuth callback POC, Refresh pipeline, JSON/API parser 상태,
  실제 응답 구조 보기 진입점을 제거했다.
- 설정 → 앱 정보 → 앱 버전(`0.1.0`) 영역을 연속 7회 탭하면 개발자 옵션을 활성화한다.
- 4~6회 탭에서는 남은 단계 안내를 표시하고, 7회 탭에서 활성화 메시지를 표시한다.
- 이미 활성화된 상태에서 탭하면 `이미 개발자입니다.`를 표시한다.
- 활성화 후 설정 하단에만 `개발자 옵션` 메뉴가 나타난다.
- 개발자 옵션에서 기존 OAuth/Callback·Refresh 진단, 실제 응답 구조, Mock 데이터, 캐시
  상태/초기화 기능에 접근할 수 있다.
- `개발자 옵션 사용 중지`를 누르면 메뉴가 즉시 사라진다. Mock 상태에서 중지하면 Mock
  provider와 Mock snapshot도 함께 정리한다.

### Persistence and safety

- `developer_mode_enabled`를 기존 Preferences DataStore에 저장한다.
- 기본값은 `false`이며 앱 재실행 시 DataStore flow가 저장값을 다시 반영한다.
- 로그아웃으로 사용량/인증 상태를 초기화해도 개발자 모드 설정 자체는 유지한다.
- 개발자 도구에는 Cookie, session ID, OAuth token/code/state, Google 이메일, 계정 식별자,
  전화번호, 전체 raw 개인정보를 추가로 표시하지 않았다.
- 숨김 상태는 `BuildConfig.DEBUG`에 의존하지 않으므로 Release 코드 경로에서도 사용할 수
  있다. Release APK 자체는 이번 요청의 필수 산출물이 아니어서 별도로 생성하지 않았다.
- OAuth, Custom Tab, callback handoff, Freet session, API WebView, JSON parser,
  UsageSnapshot, refresh pipeline, Home 실제 데이터, Widget 로직은 변경하지 않았다.

### Tests and verification

- 기본 developer mode disabled: PASS
- 6회 탭 후 비활성: PASS
- 7회 탭 후 활성화: PASS
- 4/5/6회 진행 문구: PASS
- 활성 상태 복원 및 이미 개발자 메시지: PASS
- 비활성 상태에서 developer options/mock/diagnostics 접근 정책: PASS
- 활성 상태에서 개발자 화면 내부 접근 정책: PASS
- `gradlew.bat test`: PASS
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS — 0 errors; dependency/tooling update warnings 10건
- APK: EXISTS — `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK size: 21,189,734 bytes
- APK SHA-256: `21C308AE3931CBE6C413E53FD618E807556D9A815B36A4D7C3A283716BFC274A`
- 실제 Galaxy에서 7회 탭, 앱 재시작 후 유지, 비활성화 즉시 메뉴 제거: NOT TESTED

## Responsive widget styles and per-instance configuration

### Scope

이번 작업은 기존 Freet 로그인, OAuth/Custom Tab callback handoff, Freet session,
dedicated API WebView, JSON parser, `UsageSnapshot`, repository/cache, refresh pipeline,
Home 실제 데이터, Widget refresh scheduler를 변경하지 않고 위젯 표시와 설정 계층만
확장했다. 위젯은 기존 `UsageCacheStore` snapshot을 읽고 Home과 같은 `formatBytes`를
사용하며, 자체적으로 Freet API를 호출하지 않는다.

### Implemented

- Glance `1.1.1`의 `SizeMode.Responsive`와 `LocalSize`를 사용해 실제 위젯 크기를
  `MICRO`, `SMALL`, `MEDIUM`, `LARGE`, `XLARGE`로 분류한다.
- `INFO` 스타일과 반원 Bitmap 게이지를 사용하는 `GAUGE` 스타일을 추가했다.
- 작은 크기에서는 잔여량 중심으로 표시하고, 크기가 커질수록 사용량, 전체량, 잔여
  퍼센트, 초기화일, 마지막 업데이트, 상태, 새로고침을 단계적으로 표시한다.
- 게이지는 `remainingBytes / totalBytes`에서 계산한 값을 사용하며 0/50/100%와
  비정상 퍼센트의 경계를 안전하게 처리한다.
- 위젯 배경 사용 여부, 시스템/라이트/다크 테마, 0~100% 투명도, 새로고침/업데이트
  표시 여부를 위젯별 `appWidgetId`로 저장한다. 투명도는 0%가 불투명, 100%가
  완전 투명이다.
- 표준 Android widget configure Activity를 추가했다. 취소 시 `RESULT_CANCELED`,
  저장 시 해당 `EXTRA_APPWIDGET_ID`와 `RESULT_OK`를 반환하고 해당 위젯만 갱신한다.
  이후 재설정은 큰 위젯의 `설정` 진입점 또는 런처의 표준 위젯 재설정 흐름에서
  수행할 수 있다.
- 삭제된 위젯의 per-instance 설정은 receiver의 `onDeleted`에서 정리한다. 기존
  위젯/누락된 설정은 INFO + SYSTEM + 배경 사용 + 0% 투명도 + 새로고침/업데이트
  표시 기본값으로 안전하게 동작한다.
- 위젯 XML은 가로/세로 resize를 허용하고 최소 크기를 70dp로 낮췄으며 표준
  `android:configure`와 `reconfigurable`을 연결했다.
- 모든 게이지 이미지에는 접근성 content description을 제공한다. UI는 일반 사용자
  위젯에 OAuth/API parser 상세 진단이나 민감정보를 표시하지 않는다.

### Tests and verification

- Widget config defaults, opacity clamping, style/theme enumeration, per-widget key
  isolation: PASS
- Five responsive size classes and progressive INFO/GAUGE content plan: PASS
- Gauge sweep edge cases 0/50/100/NaN/clamping and zero-total safety: PASS
- Realistic Freet snapshot uses shared formatter (`20 GB`, `308.33 MB`, `19.7 GB`): PASS
- `gradlew.bat test`: PASS
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS — 0 errors, 11 dependency/tooling/resource warnings
- APK: EXISTS — `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK size: 21,189,734 bytes
- APK SHA-256: `C799A3639D3774CE20191F2D19FF3B531FB3F766C0557E402AAFC45710FF63F7`
- Galaxy One UI widget add/resize/configure/tap/refresh visual E2E: NOT TESTED

## Galaxy widget UI regression correction

### Findings from the reported Galaxy run

- Background-off mode made the opacity control meaningless, because the control remained
  visible even though no widget background was drawn.
- A square size such as `180 x 180 dp` was classified as `SMALL` by the previous
  width-first thresholds, so the 2x2 layout omitted the total value.
- The gauge arc was square inside its source bitmap, but the bitmap width was based on the
  full `LocalSize` while the Glance Image was laid out after root padding. That mismatch
  allowed the rendered image to be horizontally rescaled.
- The previous MICRO information path still included labels and the full spaced formatter,
  allowing the important number/unit to wrap.

### Fixes

- Opacity controls are now only visible when `backgroundEnabled` is true. Background-off
  widgets draw no container background or corner shape; their text and gauge remain visible.
- Responsive classification uses the shorter available axis for the 1x1/2x1/1x2/2x2
  distinction. `180 x 180 dp` is now `MEDIUM`, while `180 x 120 dp` and `82 x 180 dp`
  remain `SMALL`.
- MICRO uses a dedicated compact formatter: examples are `19.7G`, `999M`, and `308M`.
  It renders only the remaining value, avoiding the previous label/line-wrap combination.
  The MICRO GAUGE stacks the compact value below the arc and omits secondary details.
- The gauge source image width now matches the post-padding content width. Arc geometry is
  calculated from `radius = min(availableWidth / 2, availableGaugeHeight)` and uses a
  square circle bound (`boundsWidth == boundsHeight`) with rounded, constant-width strokes.
  The preview uses the same geometry rules.
- Foreground choices are explicit and high-contrast: `시스템`, `어두운 글자`, and `밝은
  글자`. Primary text is near-black or near-white; muted gray is not used as primary text.
- The settings preview now reflects INFO/GAUGE, foreground theme, background on/off, and
  opacity. Latest actual Glance width/height and size class are recorded without sensitive
  data and shown only inside the existing hidden developer diagnostics screen.
- OAuth, API, parser, raw KiB handling, `UsageSnapshot`, repository/cache, and refresh
  pipeline were not changed.

### Regression and verification

- MICRO `19.7 GB` -> one-line `19.7G`: PASS
- MICRO `999.81 MB` / `308.33 MB` -> compact one-line values: PASS
- 2x2-style `180 x 180 dp` -> MEDIUM with remaining/used/total: PASS
- Wide and tall gauge geometry -> square circle bounds: PASS
- Invalid gauge dimensions -> rejected safely: PASS
- Background-off opacity control -> hidden: PASS
- Foreground theme labels and contrast mapping: PASS
- Actual Glance dp size diagnostic formatting: PASS
- `gradlew.bat test`: PASS
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS — 0 errors, 11 dependency/tooling/resource warnings
- APK: EXISTS — `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK size: 21,189,734 bytes
- APK SHA-256: `E41372E3294D10E4FD60450F13AB3A52C24429350EA29EA17A90A9746DBDB116`
- Galaxy One UI visual E2E after this correction: NOT TESTED

## Galaxy responsive gauge and resize correction

### Scope and root-cause finding

이번 수정은 위젯 표시 계층에만 적용했다. OAuth, Custom Tab, callback handoff,
Freet session, API WebView/endpoint, JSON parser, raw KiB 처리, `UsageSnapshot`,
Repository, refresh coordinator는 변경하지 않았다.

Android Canvas의 각도 규칙을 코드와 테스트로 고정했다. Canvas에서 0°는 오른쪽,
90°는 아래쪽이며 양의 sweep은 시계방향이다. 따라서 `start=180°`에 양의 `180°`를
주면 왼쪽 endpoint에서 270°의 위쪽 중앙을 거쳐 오른쪽 endpoint로 진행하는 위
반원(`∩`)이 된다. 기존 renderer의 `start=180°`, `sweep=-180°`는 90°의 아래쪽을
통과하는 아래 반원(`∪`)을 그렸고, bitmap은 상단 반원 높이만 확보했기 때문에 arc가
대부분 bitmap 밖으로 잘렸다. 이것이 Galaxy 실기기에서 게이지가 거의 또는 전혀
보이지 않은 직접 원인이다.

### Implemented

- `GAUGE_START_ANGLE_DEGREES = 180°`, `GAUGE_TOP_SEMICIRCLE_SWEEP_DEGREES = +180°`를
  renderer와 Compose preview에서 공유한다. Progress sweep은 raw byte 기반 remaining
  fraction `0..1`을 `0..+180°`로 변환하며, `remainRate`는 사용하지 않는다.
- Gauge geometry가 stroke safe padding을 반영하고, `radius = min(drawableWidth / 2,
  drawableHeight)`로 계산되는 정사각형 circle bounds를 사용한다. `boundsWidth ==
  boundsHeight`를 유지하며 round cap/anti-alias stroke를 사용한다.
- Bitmap은 실제 post-padding content width와 같은 비율로 생성하고, wide/tall 위젯에서
  bitmap 전체를 arc oval로 사용하지 않는다. Top arc의 visible half와 round endpoint가
  bitmap 안에 남도록 safe padding을 둔다.
- `SizeMode.Exact`의 `LocalSize` dp에서 폭과 높이를 각각 `W1..W6`, `H1..H6`로 분류하고, aspect ratio로
  `COMPACT_SQUARE`, `BALANCED`, `WIDE`, `ULTRA_WIDE`, `TALL`, `ULTRA_TALL`을 계산한다.
  셀 수를 runtime 기준으로 하드코딩하지 않는다.
- 공통 layout rule로 `MICRO`, `WIDE_DASHBOARD`, `TALL_DASHBOARD`,
  `COMPACT_DETAIL`, `FULL_DETAIL`을 선택한다. 1x1은 compact remaining 한 줄,
  2x1/3x1 이상은 가로 dashboard, 1x2/1x3 이상은 세로 dashboard, 2x2는
  remaining/used/total, 큰 정사각형은 metric detail을 사용한다. 높이 H1인 wide
  INFO는 한 줄 horizontal dashboard, GAUGE는 게이지와 최대 2행 metric panel로 제한한다.
- GAUGE는 wide orientation에서만 START 배치하고, 정사각형·세로형은 TOP 배치를
  사용한다. 1x1에서는 게이지와 compact remaining만 표시하고, 큰 크기에서는
  used/total/percent/update/status/carrier/refresh를 남은 공간에 단계적으로 추가한다.
- MICRO 및 height/width가 좁은 dashboard는 `19.7G`, `308M` 같은 compact formatter를
  사용하고 핵심 값 Text에 `maxLines=1`을 적용해 숫자와 단위가 줄바꿈되지 않게 한다.
  큰 크기는 기존 shared byte formatter를 유지한다.
- 위젯 preview도 동일한 양의 sweep, safe square geometry, responsive layout plan과
  compact/full formatter를 사용한다.
- 개발자 진단의 최신 위젯 크기 정보에 실제 dp, 폭/높이 class, orientation, 선택 layout,
  nominal gauge diameter를 표시한다. 기존 설정과 위젯별 migration 동작은 보존한다.

### Required result matrix

- 1x1: MICRO, remaining-only, compact one-line: PASS (unit/layout test)
- 2x1: WIDE_DASHBOARD, horizontal metric layout: PASS (layout plan test)
- 1x2: TALL_DASHBOARD, vertical metric layout: PASS (layout plan test)
- 2x2: COMPACT_DETAIL, remaining/used/total: PASS (layout plan test)
- 3x1~3x3: width/height/orientation responsive rules: PASS (layout-state test)
- 4x1~4x4: wide/tall/full detail rules: PASS (layout-state test)
- 5x1~5x5: W5/H5 density rules: PASS (layout-state test)
- 6x1~6x6: W6/H6 and large square rules: PASS (layout-state test)
- Wide layout: IMPLEMENTED
- Tall layout: IMPLEMENTED
- Square layout: IMPLEMENTED
- Responsive information density: IMPLEMENTED
- No-wrap: PASS (compact formatter and single-line core/metric text); Galaxy visual confirmation remains pending
- Gauge orientation: TOP SEMICIRCLE
- Gauge start angle: `180°`
- Gauge total sweep: `+180°`
- Gauge progress sweep: `0°` at 0%, `+90°` at 50%, `+180°` at 100%
- Gauge circular bounds: PASS (all 36 representative dp combinations)
- Gauge clipping: PASS (top/center/endpoints plus round-cap safe-padding invariants); Galaxy bitmap visual check pending
- Opacity when background off: HIDDEN (existing behavior retained)
- Transparent foreground contrast: PASS by explicit light/dark palette mapping

### Verification

- Focused widget layout/config/gauge/status tests: PASS
- `gradlew.bat test`: PASS — 157 tests, 0 failures, 0 errors
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS — 0 errors, 11 dependency/tooling/resource warnings
- APK: EXISTS — `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK size: 21,189,734 bytes
- APK SHA-256: `416E8E14004CA0964C7C0635CEE19BF0A5E8A9520C2B43DA2E6DA52E7351FCCE`
- Galaxy One UI visual E2E after this fix: NOT TESTED

## Galaxy widget visual-config rendering correction

### Confirmed data flow and root cause

`DataUsageWidgetConfigActivity`는 `EXTRA_APPWIDGET_ID`로 받은 ID를 사용해
`WidgetConfigStore.save(appWidgetId, config)`를 호출하고, 실제 renderer는
`GlanceAppWidgetManager.getAppWidgetId(glanceId)`로 같은 ID를 복원해
`WidgetConfigStore.read(appWidgetId)`를 호출한다. Style, theme, background enabled,
transparency, refresh, updated 설정은 모두 instance별 key로 저장된다. 따라서 renderer가
무조건 default config를 읽는 문제는 아니었다.

실제 적용 경계에는 세 가지 문제가 있었다.

1. `LIGHT/DARK`가 배경 밝기처럼 이름 붙고 UI에서는 반대 방향의 글자색으로 표시돼
   foreground 설정 의미가 불명확했다.
2. preview와 실제 Glance renderer가 별도의 색상 해석 코드를 사용했으며, 실제 root에는
   `appWidgetBackground()` 지정이 없었다.
3. 저장 직후 해당 widget ID의 `update()`에서 발생한 예외를 `runCatching`으로 완전히
   무시해 오래된 RemoteViews가 남아도 확인할 수 없었다.

### Implemented visual configuration

- `WidgetTheme`을 `SYSTEM`, `LIGHT_FOREGROUND`, `DARK_FOREGROUND`로 명확히 정의했다.
  기존 저장값 `DARK`는 `LIGHT_FOREGROUND`, `LIGHT`는 `DARK_FOREGROUND`로 migration해
  기존에 보이던 의미를 보존한다.
- `resolveWidgetVisualConfig()` 하나가 INFO, GAUGE, 설정 preview, 개발자 진단의 primary,
  secondary, progress, track, background, alpha, stroke를 모두 계산한다.
- 모든 Glance Text에 공식 `ColorProvider(day, night)`를 통해 primary/secondary/accent 색을
  명시한다. remaining은 항상 primary foreground이고 accent는 gauge progress 및
  refresh/settings affordance에만 사용한다.
- Background ON은 root `fillMaxSize()` container에 alpha가 포함된 surface color,
  rounded corner와 `appWidgetBackground()`를 적용한다. Background OFF는 background
  modifier를 추가하지 않는다.
- transparency 매핑은 0%=255, 25%=191, 50%=128, 75%=64, 100%=0이다. Background OFF면
  alpha 설정을 무시하고 renderer 자체를 생략한다.
- 설정 저장 직후 같은 appWidgetId의 Glance ID만 `update()`하며 update 실패를 더 이상
  무시하지 않는다. 설정 화면 재진입은 같은 ID의 저장값을 decode한다.
- 개발자 Widget 진단에 ID, actual dp, layout, style, configured theme, background ON/OFF,
  transparency, resolved foreground, resolved primary ARGB, resolved alpha, gauge diameter,
  gauge stroke dp를 기록한다.

### Gauge stroke

- Previous: `diameterPx * 0.12`, 최대 `18px`. xxhdpi에서는 최대 약 `6dp`여서 Galaxy에서
  매우 얇게 보였다.
- New: gauge diameter dp의 13%를 기준으로 size class별 clamp한다.
  MICRO 8~10dp, SMALL 9~12dp, MEDIUM 13~16dp, LARGE 16~20dp, XLARGE 19~24dp.
- 180x180dp 2x2 fixture: diameter 125dp, stroke 16dp. 기존 xxhdpi 약 6dp 대비 2배 이상이다.
- TOP semicircle, square circle bounds, round cap, antialias, aspect ratio는 유지했다.
- 36개 representative dp fixture에서 새 stroke가 radius보다 작고 arc top/양 endpoint가
  bitmap 안에 남는지 검증했다.

### Verification

- Per-widget config loaded: YES
- Text foreground applied: YES
- Background rendered: YES
- Background OFF: renderer omitted
- INFO color resolver: PASS
- GAUGE color resolver: PASS
- Gauge crop geometry: PASS
- `gradlew.bat test`: PASS — 166 tests, 0 failures, 0 errors
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS — 0 errors, 11 dependency/tooling/resource warnings
- APK: `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK size: 21,189,734 bytes
- APK SHA-256: `C161FE90F8A91FDE2627EC24CEFA6ECD63FC5ADC9AC1E6B4EA55CF5D146A90B1`
- Galaxy E2E after fix: NOT TESTED

## Open-bottom circular-ring gauge and compact-wide widget correction

### Geometry and rendering

- Gauge style: `OPEN_BOTTOM_CIRCULAR_RING`
- Android Canvas start angle: `135°` (lower-left endpoint)
- Clockwise sweep: `270°`, passing through the top and ending at `405°` / `45°`
- Bottom gap: `90°`, centered on the bottom of the circle
- Track and remaining progress share the same start, circular bounds, stroke, and round caps.
- Progress is `270° × clamp(remainingBytes / totalBytes, 0..1)`; `remainRate` is not used.
- The visible arc, endpoint caps, and top are fitted into an aspect-preserving bitmap while the full-circle bounds remain square.

### Responsive UI

- Added the dp-driven `COMPACT_WIDE_DASHBOARD` layout for the representative 2x1 size (`W2 × H1`).
- 2x1 INFO uses a large compact remaining value at left and compact used/total metrics at right.
- 2x1 GAUGE uses the ring at the start and a three-line remaining/used/total metric panel at right.
- All compact values remain single-line (`19.7G`, `308M`, `20G`).
- Medium and larger gauges place remaining percentage inside the ring; the remaining data value remains the primary text.
- Preview uses the same 2x1 layout plan, gauge geometry, stroke, foreground resolver, background, and opacity resolver.

### Thickness

- Diameter ratio: `16%`, clamped by size class.
- MICRO: `8–10dp`
- SMALL: `10–12dp`
- MEDIUM: `14–17dp`
- LARGE: `16–20dp`
- XLARGE: `18–24dp`

### Verification

- Open-ring 0%, 50%, and 100% sweeps: PASS
- Circular bounds and no ellipse: PASS
- Arc/cap crop sampling across 36 representative dp fixtures: PASS
- 2x1 INFO/GAUGE content and layout-state regression: PASS
- 1x1 no-wrap and 2x2 density regression: PASS
- Per-widget visual configuration regression: PASS
- `gradlew.bat test`: PASS (172 debug unit tests, 0 failures, 0 errors)
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS (0 errors, 11 existing warnings)
- APK: `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK SHA-256: `2919A643C1D87FA67F578C6A0F73CCA4FD900A38312157208C84B1C79E09A54B`
- Galaxy E2E after fix: NOT TESTED

## System Back Navigation

### Navigation structure

DataPeek MainActivity에는 `NavHost`/`NavController`가 없고, 기존 Compose 상태인
`AppScreen`(`HOME`, `LOGIN`, `SETTINGS`, `DEVELOPER_OPTIONS`, `DIAGNOSTICS`)이
화면 전환을 담당한다. 이 구조를 유지하면서 `BackHandler` 하나를
`DataPeekApp`에 연결하고, 기존 parent flow를 `appScreenBackDestination()`으로
단일화했다. 별도 위젯 설정 Activity는 MainActivity의 BackHandler 범위 밖에 둔다.

### Implemented behavior

- 하위 화면: 기존 parent destination으로 이동한다.
  `LOGIN → HOME`, `SETTINGS → HOME`, `DEVELOPER_OPTIONS → SETTINGS`,
  `DIAGNOSTICS → DEVELOPER_OPTIONS`.
- Home root만 `DoubleBackExitController`를 사용한다. 첫 back은 앱을 종료하지 않고
  `한 번 더 누르면 종료됩니다.` Toast를 표시한다. 기존 Toast는 새 Toast 전에
  cancel하여 빠른 반복 입력으로 쌓이지 않게 했다.
- 두 번째 back은 첫 입력 후 `now < armedUntil`인 2초 이내에만 현재 MainActivity의
  `finish()`를 호출한다. `finishAffinity()`, `moveTaskToBack()`,
  `Process.killProcess()`, `System.exit()`는 사용하지 않았다.
- timeout은 영속화하지 않고 `remember { DoubleBackExitController() }`의 Activity/
  Compose 메모리에만 둔다. 화면을 바꾸는 공통 `navigateTo()`가 pending arm과 Toast를
  즉시 reset하며, Activity recreation에서는 새 controller가 생성되어 armed 상태가
  남지 않는다.
- Login에서 일반 상태의 back은 OAuth retry/reset cleanup 후 Home으로 돌아간다.
  Custom Tab 또는 callback handoff가 진행 중이면 back을 소비하고 OAuth/WebView 상태를
  reset하거나 destroy하지 않는다. WebView `canGoBack()`을 전역 back 정책으로 사용하지
  않았다.
- AndroidX `BackHandler`를 사용하므로 Android back button과 gesture navigation이
  동일한 dispatcher 경로를 사용한다. deprecated `onBackPressed()` override는 없다.
- Widget Configure Activity의 기존 `setResult(RESULT_CANCELED)` lifecycle은 변경하지
  않았다. 따라서 설정 중 back은 MainActivity double-back 정책의 영향을 받지 않는다.

### Verification

- Navigation back destination/state tests: PASS
- Home first back arms message without exit: PASS
- second back within 2 seconds exits: PASS
- exactly-after-window and repeated timeout windows reset: PASS
- navigation away/re-entry resets pending exit: PASS
- Activity recreation semantics: PASS (controller is non-persistent)
- Login back before OAuth handoff vs. during handoff: PASS (state policy test)
- `gradlew.bat test`: PASS
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS — 0 errors, 11 dependency/tooling/resource warnings
- APK: EXISTS — `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK size: 21,189,734 bytes
- APK SHA-256: `A100622D098F88A2738D9AD4CDE27BE5E1F572CA93E887429B67EE5AC7174D2E`
- Galaxy button/gesture back E2E after this fix: NOT TESTED

## Freet session persistence, expiry, and re-login UX

### Session persistence

- `UsageViewModel` now restores the last cached `UsageSnapshot` into Home while the
  existing dedicated Freet API WebView performs the session probe.
- A cached snapshot causes a probe even when the local encrypted session marker or
  WebView cookie check is temporarily unavailable. The app no longer converts that
  condition into an unverified login-expired state before the probe.
- Startup with neither cache nor local session marker remains on the normal Home
  screen and presents the existing login entry point; it does not auto-start OAuth.
- Foreground refreshes use the existing coordinator and skip a second foreground
  refresh within 30 seconds. Manual and login refreshes remain explicit and are not
  blocked by this foreground interval.

### Session state and cache policy

- Added transient states: `UNKNOWN`, `AUTHENTICATED`, `CHECKING`, `SESSION_EXPIRED`,
  `NETWORK_ERROR`, and `SERVER_ERROR`.
- 401, 403, Freet `/login` redirect, and the existing auth-error response path map to
  `SESSION_EXPIRED`.
- Timeout, body-read, navigation/request, and network failures map to
  `NETWORK_ERROR`; parser and non-2xx server failures remain non-authentication
  failures. Network/parser failures never request re-login.
- `UsageCacheStore.saveError()` already retained the snapshot; the UI now explicitly
  keeps that snapshot visible for both temporary failures and confirmed expiry.
  Cached data is cleared only by the explicit logout/cache-clear paths.
- Home shows the last successful value and timestamp/relative age while showing
  either update failure or login-required status. It never replaces a failure with
  zero data.

### Re-login, logout, and widget behavior

- Re-login invokes the existing Freet WebView → Custom Tab → callback handoff →
  dedicated usage API path without changing OAuth, cookies, endpoint, parser, or
  User-Agent behavior.
- Explicit logout now cancels the active refresh request, resets the existing OAuth
  attempt, clears Freet session markers/cookies and cached usage, and requests a
  widget update. No cookie/token/code/state values are read or logged.
- A widget with a confirmed login/session error keeps its last snapshot, shows
  `로그인 필요` where the selected layout has room, and opens MainActivity directly
  into the existing login screen when tapped.
- Developer-only diagnostics expose only auth state, last successful snapshot time,
  last API HTTP status, expiry reason text, and cache timestamp. Credentials,
  cookies, tokens, authorization codes, state, and account identifiers are not
  displayed.

### Verification

- Session error classification tests: PASS
- Cached snapshot retained for network failure: PASS
- Cached snapshot retained for confirmed session expiry: PASS
- Cached snapshot probe policy: PASS
- Foreground refresh interval policy: PASS
- Refresh cancellation prevents stale request ownership: PASS
- Stale-cache age presentation: PASS
- Widget login-required classification and login entry point: PASS
- `gradlew.bat test`: PASS — 394 tests, 0 failures, 0 errors
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS — 0 errors
- APK: EXISTS — `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK size: 21,189,734 bytes
- APK SHA-256: `8A11C4995539C11727257BCC35BC69820EE193F2776FC328ABE71F84608F4AA4`
- Galaxy session persistence/reboot/re-login E2E after this change: NOT TESTED

## Automatic Refresh

### Policy and scheduling

- Default for a new installation: automatic refresh `ON`, `1 hour`.
- Supported choices remain `30분`, `1시간`, `3시간`, `6시간`, and `수동`.
  The existing persisted manual setting (`0`) is treated as automatic refresh off;
  enabling automatic refresh from that state restores the default `1시간` interval.
- `UsageRefreshScheduler` uses the fixed unique periodic work name
  `usage_periodic_refresh`, requires `NetworkType.CONNECTED`, enforces the WorkManager
  minimum of 15 minutes, and uses `UPDATE` semantics after cancelling the previous
  schedule. Settings changes reconfigure it without requiring an app restart.
- The setting is stored in the existing DataStore. The enabled flag is separate from
  the interval so periodic work can be disabled without deleting the selected interval.

### Single refresh pipeline and safe background fallback

- Foreground startup/resume uses the existing Freet usage coordinator and dedicated API
  WebView. Cached data is shown immediately; a refresh starts only when the snapshot is
  stale, a background attempt marked `refreshDue`, or a session recheck is needed.
  A recent successful snapshot is not refreshed again merely because the Activity
  resumed.
- Widget refresh does not create a Worker-side WebView or a second HTTP path. It marks
  the refresh due, opens the existing `MainActivity`, and requests a `WIDGET` refresh
  through the same `FreetUsageRefreshCoordinator`.
- The WorkManager Worker intentionally does not perform a headless Freet refresh.
  Freet's verified authentication boundary is the Activity WebView; a Worker cannot
  safely provide that lifecycle without copying browser cookies or creating an
  unverified parallel client. The Worker records `refreshDue`, the background attempt
  time, `BACKGROUND` as the trigger, and `DEFERRED_TO_FOREGROUND`, then leaves the
  cached Widget value unchanged. The next foreground or Widget interaction performs
  the real API refresh.
- Freet is explicitly marked as not supporting safe headless background refresh. No
  OAuth, Custom Tab, callback, CookieManager policy, endpoint, parser, UsageSnapshot,
  or refresh WebView path was changed.

### State and failure behavior

- A successful Snapshot save clears `refreshDue` atomically after the cache update.
- Network, timeout, server, and parser failures do not delete or zero the last
  successful Snapshot and are not converted into session expiry.
- Confirmed authentication failures continue to use the existing `SESSION_EXPIRED`
  state. Automatic refresh never starts Google login UI in the background.
- Developer-only diagnostics now include automatic-refresh enabled state, configured
  interval, pending refresh, last background attempt/result, and last refresh trigger;
  no cookie, token, authorization code, state, or account identifier is recorded.

### Verification

- Foreground stale/recent/due/no-cache/session-expired policy tests: PASS
- Background fallback plan and WorkManager minimum interval tests: PASS
- Existing coordinator single-flight/concurrency and cache-retention tests: PASS
- `gradlew.bat test`: PASS — 205 debug + 205 release unit tests, 0 failures, 0 errors
- `gradlew.bat assembleDebug`: PASS
- `gradlew.bat lint`: PASS — 0 errors
- APK: EXISTS — `C:\src\DataPeek\app\build\outputs\apk\debug\app-debug.apk`
- APK size: 21,189,734 bytes
- APK SHA-256: `B36D693CAB238664B894526D0A5EDD201A6B0C1F0F8897748E9F6019339DC985`
- Galaxy background E2E (app closed, scheduled time elapsed, Widget value changed): NOT TESTED
