# 쉼표 UI 2단계 — RESTING

## 디자인
기존에 확보한 Figma 디자인 컨텍스트와 스크린샷을 재사용했다.
파일 N9WB8GDA5z0UxwcfzqUeVa / 페이지 92:74722.
가로형 56:41898, 좁은 분할형 82:59838.
CommaDesignTheme/Panel/Button/Canvas 아이콘을 재사용하며 전역 테마와 차박 모드는 수정하지 않는다.
Asta Sans 및 전용 아이콘 대신 1단계의 시스템 Sans와 대체 선형 아이콘을 계속 사용한다.

## 화면
실제 할당된 Compose 너비 900dp 이상: 왼쪽 원형 타이머, 오른쪽 온도/앱 음향/배터리 카드.
그 미만: 세로 타이머와 줄바꿈 가능한 상태 pill. 콘텐츠는 스크롤 가능하다.
원형 타이머는 파란색 남은 비율과 회색 트랙, 남은 분:초, 기존 종료 예정 시각을 표시한다.
Figma의 고정 온도 유지/빗소리/배터리 수치는 복제하지 않는다.
차량 값은 VehicleState, 음향은 CabinController 스냅샷만 사용하며 모의 차량 값을 표시한다.
5분 더와 휴식 종료 외에도 기존 10분 연장을 보존한다.
앱 무드 조명 배경과 실제 적용 중 밝기 표시, 마지막 30초 밝기 변화도 유지한다.

## 시간과 상태 정책
진행률은 remainingMillis / (plannedMinutes * 60000)의 남은 비율이다.
plannedMinutes는 최초 선택 시간 + 승인된 연장 시간의 합이다.
연장 시 분모와 남은 시간이 함께 증가하므로 처음부터 다시 시작하지 않는다.
WAKING 확인 시간은 계획 시간에 포함하지 않는다.
확인 중 연장하면 기존 manager 정책에 따라 새 남은 시간이 생기며 같은 누적 계획 시간을 분모로 쓴다.
UI에는 별도 시계/시작 상태/타이머 coroutine이 없다.
실제 세션은 SystemClock.elapsedRealtime 기반 manager 스냅샷을 쓰고 데모는 기존 가상 시계를 쓴다.
화면 재구성/재진입은 시작 액션을 호출하지 않는다.

finishResting 액션은 ViewModel의 최신 실제/데모 세션 phase를 확인한다.
RESTING에서만 기존 동기 finish -> 복구/저장 큐 전달을 실행한다.
중복 호출 또는 WAKING으로 바뀐 뒤 도착한 RESTING 콜백은 무시한다.
기존 WAKING 대화상자의 finish/extend 동작은 그대로다.

## 범위
설정 화면과 기존 WAKING/FINISHED UI, Room, 복구, 데모 시계는 유지한다.
WAKING 알람·연장/FINISHED 완료 화면 디자인은 다음 단계다.
차량 SDK, 자연음, 예측 배터리, Asta Sans/원본 아이콘 적용은 미구현이다.
프로세스 강제 종료 후 세션 복구는 기존 미지원 정책을 유지한다.

## 검증
RestingPresentationTest 7개 추가:
단조 시계와 화면 재진입, 연장 누적 비율, WAKING/연장 전환,
READY/FINISHED 분기, 중복 종료/복구, 오래된 종료 콜백, 비정상 스냅샷 경계.
기존 쉼표 33개 + 신규 7개 = 40개, ExampleUnitTest 포함 총 41개.
빌드/테스트는 COMMA_MVP.md의 영문 경로 캐시와 Android Studio JBR 사용.
실행 결과: :app:assembleDebug 및 :app:testDebugUnitTest 모두 성공 (BUILD SUCCESSFUL).
가로 1100dp, 좁은 분할 560dp, 일반 기기 360dp Preview 렌더링 성공.
첫 Preview의 오래된 생성자 캐시 오류는 IDE Gradle 빌드 후 해소됐다.
