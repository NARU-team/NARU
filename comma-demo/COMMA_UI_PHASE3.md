# 쉼표 WAKING UI 3단계

## 디자인 확인 범위
Figma 파일 N9WB8GDA5z0UxwcfzqUeVa, 페이지 92:74722.
가로형 73:46476은 기존에 확보한 고해상도 디자인 컨텍스트/스크린샷 재사용.
좁은 85:64089는 get_design_context와 get_screenshot을 실제 재호출했으나 둘 다 View seat 호출 한도 오류.
기존 메타데이터에서 naru_app 85:64102 / 840x1184,
card 85:64564 / 760x816, ring 85:64565 / 600x600,
알람/16:42/쉴 시간이 끝났어요 텍스트, 상태 pill 85:64572,
3개 버튼 85:64584를 확인했다.
좁은 화면의 정확한 컬러/폰트/세부 간격은 새로 확인하지 못했다.
메타데이터 배치와 확인된 가로형 스타일, 기존 쉼표 디자인 시스템으로 구성했다.
Asta Sans/전용 아이콘은 기존 시스템 글꼴과 대체 아이콘을 유지한다.

## 구현과 보존
- WAKING 분기를 전용 Compose 화면으로 교체하고 중복되는 기존 WAKING AlertDialog를 제거.
- 900dp 이상 실제 앱 영역: 왼쪽 원형 알람/오른쪽 상태 카드.
- 좁은 화면: 세로 원형 알람/줄바꿈 상태 pill. 스크롤 및 최소 48dp 이상 터치 영역.
- 시간은 session.expectedEndMillis 사용. 현재 시각이나 화면 진입 시각으로 대체하지 않는다.
- 확인 시간은 session.confirmationRemainingMillis로 표시. UI 진입 시 30초를 재시작하지 않는다.
- 제목에 TalkBack heading/Polite live region을 지정하며 매초 카운트다운을 live region으로 읽지 않는다.
- 짧은 단발 알람을 계속 울리는 중이라고 표시하지 않는다.
- 앱 밝기는 cabin 실제 스냅샷, 배터리는 현재 모의 값만 표시하며 전후 변화량을 만들지 않는다.
- wakingActions는 최신 WAKING 상태에서만 기존 extend(5/10), finish로 전달한다.
  연장 직후의 추가 탭/종료 콜백은 무시한다.
- 동일 manager/lifecycle 종료 경로로 복구 및 기록 큐 전달. Room 중복 저장 방지 회귀 테스트 추가.
- CabinEffects의 단발 톤, 화면 밝기 램프, 기존 30초 확인/자동 종료 정책, 타이머 manager와 Room 구현은 변경하지 않는다.
- 데모 확인 시간 만료 버튼은 중복 대화상자 대신 기존 개발 전용 컨트롤에 유지.
- 연장 후 기존 RESTING 화면은 최신 누적 plannedMinutes와 remainingMillis로 진행률 계산.
- 기존 테스트는 삭제하지 않으며 WAKING 목적지가 전용 화면이 된 기존 분기 테스트 기대값만 갱신.

## 미구현
FINISHED 디자인, 차량 제어, 자연음, 배터리 변화량/예측은 범위 제외.
차박 모드와 전역 테마 수정 없음.
좁은 화면의 원본 스크린샷과 세부 스타일은 Figma 한도 해제 후 추가 대조가 필요하다.

## 검증
WakingPresentationTest 9개 추가. 기존 41개 포함 총 50개(쉼표 49개 + 기본 예제 1개).
기존 영문 경로 Gradle 캐시 build/comma-gradle-home 및 Android Studio JBR 사용.
:app:assembleDebug 및 :app:testDebugUnitTest 성공. IDE 결과 50 passed, 0 skipped, 0 failed.
1100dp 가로형, 560dp 분할형, 360dp 일반 기기 Compose Preview 렌더링 성공.
첫 Preview의 기존 private 카드 캐시 충돌은 공용 상태 컴포넌트 분리 후 해소.

## 변경 파일
생산 코드 경로: app/src/main/java/com/naru/app/comma/
- CommaWakingScreen.kt (신규)
- WakingPresentation.kt (신규)
- CommaStatusComponents.kt (신규 공용 상태 카드/pill)
- CommaRestingScreen.kt (동일 표시 컴포넌트를 공용 파일로 이동)
- RestingPresentation.kt (WAKING 목적지)
- CommaSettingsScreen.kt (화면 분기 연결)
- CommaRoute.kt (대화상자 제거, 데모 컨트롤 유지)
- RestViewModel.kt (현재 상태를 확인하는 WAKING 액션 래퍼)

테스트 경로: app/src/test/java/com/naru/app/comma/
- WakingPresentationTest.kt (신규 9개)
- RestingPresentationTest.kt (기존 테스트 유지, 목적지 기대값 갱신)

문서: COMMA_UI_PHASE3.md
