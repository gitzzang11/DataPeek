---
name: DataPeek
description: 삼성 기본 앱에 가까운 한국어 네이티브 사용량 화면
colors:
  primary: "#0066CC"
  on-primary: "#FFFFFF"
  primary-container: "#E5F0FF"
  background: "#F2F2F2"
  surface: "#FFFFFF"
  on-surface: "#171717"
  secondary-text: "#626262"
  surface-variant: "#EAEAEA"
  divider: "#E5E5E5"
  outline: "#808080"
  error: "#B3261E"
  dark-primary: "#80B5FF"
  dark-on-primary: "#002F60"
  dark-primary-container: "#143354"
  dark-background: "#000000"
  dark-surface: "#171717"
  dark-on-surface: "#F7F7F7"
  dark-secondary-text: "#B5B5B5"
  dark-surface-variant: "#303030"
  dark-divider: "#353535"
  dark-outline: "#858585"
  dark-error: "#FFB4AB"
typography:
  display-medium:
    fontFamily: "Android system sans-serif"
    fontSize: "44sp"
    fontWeight: 700
    lineHeight: "52sp"
  headline-medium:
    fontFamily: "Android system sans-serif"
    fontSize: "28sp"
    fontWeight: 700
    lineHeight: "36sp"
  title-large:
    fontFamily: "Android system sans-serif"
    fontSize: "22sp"
    fontWeight: 600
    lineHeight: "30sp"
  title-medium:
    fontFamily: "Android system sans-serif"
    fontSize: "18sp"
    fontWeight: 500
    lineHeight: "26sp"
  body-large:
    fontFamily: "Android system sans-serif"
    fontSize: "17sp"
    fontWeight: 400
    lineHeight: "25sp"
  body-medium:
    fontFamily: "Android system sans-serif"
    fontSize: "15sp"
    fontWeight: 400
    lineHeight: "23sp"
  body-small:
    fontFamily: "Android system sans-serif"
    fontSize: "13sp"
    fontWeight: 400
    lineHeight: "20sp"
  label-large:
    fontFamily: "Android system sans-serif"
    fontSize: "16sp"
    fontWeight: 600
    lineHeight: "24sp"
rounded:
  small: "12dp"
  medium: "20dp"
  large: "26dp"
  extra-large: "30dp"
spacing:
  tight: "4dp"
  small: "8dp"
  compact: "12dp"
  regular: "16dp"
  inset: "20dp"
  generous: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    typography: "{typography.label-large}"
    padding: "14dp 24dp"
  button-text:
    textColor: "{colors.primary}"
    typography: "{typography.label-large}"
  group:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.large}"
  setting-row:
    typography: "{typography.body-large}"
    padding: "16dp 20dp"
  remaining-value:
    textColor: "{colors.primary}"
    typography: "{typography.display-medium}"
---

# Design System: DataPeek

## Overview

**Creative North Star: "삼성 기본 앱에 가까운 데이터 화면"**

사용자가 선택한 Samsung One UI 방향을 Android Compose로 구현한다. 큰 제목과 여유 있는 보기 영역, 둥근 목록 그룹, 하단의 주요 버튼으로 구성하며 한국어 내용과 잔여량이 먼저 읽힌다. 시스템 글꼴과 네이티브 Material 3 동작을 사용한다.

이 문서는 실제 구현을 기록한다. 기준은 OneUiTheme.kt, OneUiComponents.kt, 홈·설정·예측·위젯 설정 화면과 .impeccable/review/의 에뮬레이터 캡처다. PRODUCT.md의 시각적 약속을 유지하며 Samsung 독점 글꼴이나 SDK를 요구하지 않는다.

**Key Characteristics:**
- 중립 배경과 단색 표면 위의 파란 잔여량·주요 동작.
- 큰 제목이 스크롤에 따라 작은 앱 바로 접히는 구조.
- 설정은 그룹 목록으로, 설명과 상태는 한국어 문장으로 표현.
- 시스템 다크 테마와 큰 글자 설정을 따르는 네이티브 화면.

## Colors

### Primary

파란 강조색은 잔여량, 진행 막대, 선택 컨트롤, 주요 버튼에 사용한다. 다크 테마는 밝은 파랑과 어두운 버튼 글자 조합으로 전환한다. 진행 막대의 트랙은 primary-container 역할을 사용한다.

### Neutral

밝은 화면은 옅은 회색 배경 위 흰 그룹, 어두운 화면은 검정 배경 위 짙은 회색 그룹이다. 보조 글자와 안쪽 구분선은 각각 secondary-text와 divider 역할을 따른다. 대화상자의 surfaceContainerHigh도 그룹과 같은 중립 표면이다. 위젯은 같은 글자·강조 팔레트를 사용하되 사용자 지정 배경 투명도를 보존한다.

### Status

소진 위험과 소진 완료 제목은 error 역할로 표시한다. 상태 의미는 색과 함께 문장으로 전달하며, 만료·조회 실패·로딩·테스트 데이터도 각각 설명한다.

**The Semantic Color Rule.** 화면은 MaterialTheme 역할을 사용하고, 테마에 따라 강조색·표면·글자색을 함께 전환한다.

## Typography

**Display / Body Font:** Android 시스템 sans-serif. Samsung 전용 글꼴을 번들하지 않는다. frontmatter의 dp·sp는 네이티브 단위이며 CSS 단위가 아니다.

잔여량은 displayMedium, 펼친 앱 바 제목은 Material 3의 headlineMedium에 Bold를 적용한다. 빈 화면 안내는 headlineMedium, 예측 제목은 titleLarge에 Bold를 적용한다. 목록 제목은 bodyLarge, 요약과 섹션 이름은 bodyMedium, 출처·주의 문장은 bodySmall이다. 버튼은 labelLarge를 사용한다. titleLarge의 기본 두께는 SemiBold지만 예측과 위젯 잔여량은 Bold로 명시한다.

폰트 크기는 sp로 시스템 확대를 따른다. 데이터 세부 행은 FlowRow로 라벨과 값을 다음 줄로 보낼 수 있다. 위젯 미리보기의 짧은 수치는 한 줄 표시를 사용한다.

## Layout

- 홈·설정·개발자 옵션·위젯 설정: 중앙 정렬 단일 열. 본문 최대 폭(720dp), 좌우 여백(20dp), 자식 간격(16dp), 하단 여백(24dp).
- 큰 앱 바의 펼친 높이는 보통(220dp), 창 높이가 480dp 미만이면(112dp). 제목은 펼쳤을 때 가운데, 절반 이상 접히면 시작 방향으로 정렬한다.
- 하단 주요 버튼: 최소 높이(52dp), 최대 폭(672dp), 바깥 좌우 여백(24dp)과 세로 여백(12dp), 시스템 내비게이션 영역 여백.
- 그룹 안 본문 패딩(24dp), 설정 행 최소 높이(72dp)와 패딩(가로 20dp·세로 16dp), 안쪽 구분선 여백(20dp).
- 설정 라디오 대화상자 행 최소 높이(56dp), 위젯 선택 행 최소 높이(64dp). 행 전체가 선택 영역이다.
- 태블릿에서도 본문은 최대 폭을 유지한 단일 열이다. 로그인과 진단은 작은 앱 바를 사용하며 진단 본문은 별도 밀도의 스크롤 목록이다.

**The Readable Width Rule.** 큰 화면에서도 일반 화면 본문을 720dp 안에 두고, 큰 글자에서는 세부 값의 줄바꿈을 허용한다.

캡처 검토 범위: 휴대폰 밝음·어두움, 840×1800/420dpi·fontScale 1.3의 좁은 화면, 가로 화면, 1600×2560/240dpi의 태블릿 크기. 캡처는 익명 fixture 데이터이며 실제 통신사 로그인과 런처 위젯 저장 완료를 입증하지 않는다.

## Elevation & Depth

일반 그룹은 그림자 대신 배경과 표면의 명도 차이로 분리한다. 그룹 Surface와 위젯 미리보기는 평면이며 장식 그라데이션을 사용하지 않는다. 대화상자·버튼의 네이티브 눌림·비활성·포커스 동작은 Material 3 구현을 따른다. 별도의 커스텀 그림자나 모션 토큰은 없다.

## Shapes

일반 그룹과 위젯 미리보기는 큰 둥근 모서리(large)를 공유한다. 작은 모서리는 잔여량 막대의 클리핑에 사용한다. 주요 버튼은 Material 3 기본 캡슐형이며 그룹 모서리와 구별된다. 구분선은 그룹 가장자리까지 닿지 않도록 안으로 들인다.

## Components

### Buttons

하단 주요 버튼은 현재 상태에 따라 로그인·다시 로그인·다시 시도·새로고침·저장으로 표시한다. 조회 중에는 비활성화하고 진행 중 문장을 표시한다. 완료·취소는 TextButton, 진단의 추가 동작은 OutlinedButton이다. 버튼의 네이티브 터치 영역과 ripple·포커스·비활성 상태를 유지한다.

### Groups and rows

OneUiGroup은 단색 표면과 큰 둥근 모서리로 목록을 묶는다. 설정 행은 제목, 선택적 요약, 우측 컨트롤 또는 화살표 순서다. 스위치는 행 전체에 Switch 의미를 제공하며 내부 Switch에 별도 클릭을 중복 등록하지 않는다. 라디오는 선택 그룹과 행 의미를 사용한다.

### Navigation

홈·설정 계열은 접히는 큰 앱 바를 사용한다. 뒤로와 설정은 drawable 아이콘과 접근성 설명을 사용한다. 로그인과 진단은 작은 앱 바를 유지해 콘텐츠 공간을 확보한다. 공식 통신사 WebView 내부 스타일은 앱 디자인 시스템의 적용 범위 밖이다.

### Remaining data and forecast

잔여량은 파란 큰 숫자와 높이(10dp)의 가로 진행 막대로 보여 주고, 아래에 사용량·전체량·초기화 정보를 배치한다. 예측은 제목, 관측 수치, 설명 순서이며 위험 시 제목에 error 역할을 적용한다. 마지막 정상 데이터와 조회 실패 안내가 함께 표시될 수 있다.

### Usage history

홈 앱 바의 막대 아이콘과 예측 카드의 "일별 사용 기록" 행에서 진입하는 하위 화면이다. 하단 탭이나 새 내비게이션 요소를 더하지 않고 설정과 같은 접히는 큰 앱 바, OneUiGroup, OneUiSettingRow를 사용한다. 구성은 기간 선택(이전·다음 화살표), 예측 요약, 일별·누적 분할 버튼과 Canvas 그래프, 날짜별 표, 초기화일 기준 행, 기록 방식 설명 순서다. 그래프 색은 primary(사용량)와 error(예상 하루 사용량·전체 제공량 점선)만 쓰고, 추정 막대는 색이 아니라 윤곽선으로 구분한다. 오늘은 primaryContainer 배경 띠로 표시하며 그래프 설명과 같은 내용을 표가 제공한다. "이번 달은 걱정 없어요"는 기본 색, 이번 달 소진 예상은 error 색 제목이다.

### Widget configuration

정보형·게이지형, 시스템/밝은/어두운 글자, 배경과 투명도, 표시 항목을 그룹 목록으로 편집한다. 미리보기는 동일한 WidgetVisualConfig 색상·투명도 계산을 사용하며 열린 원형 게이지와 둥근 선 끝을 보여 준다. 투명도는 0% 불투명·100% 완전 투명이고 배경을 끄면 슬라이더를 숨긴다. 미리보기의 샘플 수치는 사용자의 실제 조회값이 아니다.

### Panel illustrations

.impeccable/design.json의 HTML/CSS는 네이티브 요소의 밝은 테마 시각 참고용 번역이다. Compose 런타임·sp 확대·Android ripple을 재현하지 않으며 제품 코드나 검증된 웹 UI가 아니다. 현재 앱 고유의 텍스트 입력 필드와 chip은 없어 이들 컴포넌트를 만들지 않는다.

## Do's and Don'ts

### Do:
- **Do** 중립 표면, 파란 주요 동작, 안쪽 구분선의 역할을 유지한다.
- **Do** 큰 글자에서 라벨·값·설명이 자연스럽게 줄바꿈하고 스크롤되게 한다.
- **Do** 만료·오류·조회 중·테스트 데이터를 설명 문장과 동작 이름으로 구분한다.
- **Do** 사용자 위젯의 스타일·색상·배경 투명도·표시 항목 선택을 보존한다.

### Don't:
- **Don't** 일반 그룹에 장식 그림자나 그라데이션을 추가한다.
- **Don't** 시스템 테마와 무관한 고정 글자색을 일반 화면에 사용한다.
- **Don't** 공식 로그인 WebView의 웹페이지 내부를 앱 화면처럼 재디자인한다.
- **Don't** 테스트 캡처나 위젯 미리보기 수치를 실제 통신사 조회 결과로 표현한다.
