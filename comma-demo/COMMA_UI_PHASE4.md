# 쉼표 FINISHED UI 4단계

## Figma 확인 한계
파일 N9WB8GDA5z0UxwcfzqUeVa / 페이지 92:74722.
73:47134, 85:64654 각각 get_design_context와 get_screenshot을 실제 호출했으나 4회 모두 View seat 호출 한도 오류.
페이지 get_metadata도 같은 오류. 이번 세션 캐시에는 두 완료 프레임의 메타데이터/이미지가 없었다.
따라서 원본 완료 화면의 세부 배치/컬러/폰트/아이콘을 확인했다고 주장하지 않는다.
기존에 검증한 CommaDesignTheme/Panel/Button/Canvas 대체 아이콘과 900dp 기준 반응형 규칙으로 임시 완료 UI 구성.
가로형 2열, 좁은 화면 세로 카드 배치, 요약/복구/측정/저장/홈/새 휴식 설정을 제공한다.
원본과의 일치 확인은 한도 해제 후 수행할 별도 과제다.
시스템 글꼴/대체 아이콘을 유지한다.

## 실제 데이터
Room RestRecordEntity/RestRecord에는 ID, 시작/종료 시각, 계획 분, 실제 경과 시간, 종료 사유만 존재.
온도 전후/배터리 전후/피드백/복구 결과는 기존 데이터에 없었다.
actual elapsedMillis와 endedAt을 그대로 요약한다.
TIMER 종료는 기존 정책상 확인 대기 시간을 제외하고 원래 deadline에 기록된다.
완료 화면 진입 시각을 종료 시각으로 대체하지 않는다.

## 복구 증거
RestSession에 세션 내 RestorationReport를 추가했다. Room 스키마는 변경하지 않았다.
- 앱 내부 설정: 기존 cabin.apply(previous) 후 StateFlow 값이 원래 설정과 일치하면 성공.
- apply 예외/값 불일치면 실패. 예외가 기록 생성/큐 제출을 막지 않게 한다.
- 이전 상태가 없으면 확인 불가.
- 실제 기기 창 밝기/음향 정리: CabinEffects의 반영 ACK가 없어 확인 불가.
- 차량 공조/조명: SDK 미연동이므로 미지원. 차량이 복구됐다고 표시하지 않는다.
- Mock 차량과 데모의 독립 앱 설정을 구분해 표시한다.
이 결과는 현재 세션 메모리에만 존재하며 앱 재시작 후 과거 Room 기록의 복구 성공 여부를 추정하지 않는다.
실기기 효과 확인 및 복구 결과 영구 저장은 별도 확장 과제다.

## 초기화와 기록 안전성
화면은 읽기 전용이며 finish/restore/enqueue를 호출하는 effect가 없다.
기존 완료 AlertDialog를 제거하고 전용 FINISHED 목적지 사용.
ViewModel.prepareNextRest -> manager.prepareNext는 FINISHED에서만 READY로 전환한다.
홈은 초기화 후 홈으로 이동하고 새 휴식은 초기화 후 설정 화면으로 이동한다. 자동 시작하지 않는다.
두 경로 모두 앱 범위 RestHistoryStore 및 pending map을 지우지 않는다.
데모는 완료 상태에서 기존 exitDemo로 분리하고 실제 이전 세션이 FINISHED면 같은 안전 경로로 준비한다.
저장/조회 확인은 해당 완료 record ID가 history에 있는지로 판단하고, 저장 큐 대기/오류를 별도로 표시한다.
다시 저장/조회 버튼은 기존 retry 정책 그대로 사용.
온도 변화/배터리 사용량은 측정 정보 없음, 피드백은 미지원으로 표시한다.

## 보존
설정/RESTING/WAKING, 30초 확인 시간, 5/10분 연장, 단조 시계, 데모, 기존 Room 스키마/저장 큐 유지.
차박 모드/전역 테마/차량 제어/자연음/예측 기능 수정 없음.

## 변경 파일
생산 코드 경로 app/src/main/java/com/naru/app/comma/:
- FinishedPresentation.kt (신규 모델/표시 함수)
- CommaFinishedScreen.kt (신규 화면/Preview)
- RestSessionManager.kt (복구 결과와 FINISHED 전용 준비 경로)
- RestViewModel.kt (완료 후 홈/설정 안전 초기화)
- RestingPresentation.kt (완료 목적지)
- CommaSettingsScreen.kt (완료 화면 연결)
- CommaRoute.kt (내비게이션/중복 완료 대화상자 제거)
테스트 경로 app/src/test/java/com/naru/app/comma/:
- FinishedPresentationTest.kt (신규 10개)
- RestingPresentationTest.kt (기존 7개 유지, 완료 목적지 기대값 갱신)
문서 COMMA_UI_PHASE4.md.

## 검증
기존 50개 + 추가 10개 = 총 60개.
추가 검증: 재진입, 복구 성공/실패/미확인/미지원, 저장 실패 후 초기화/다음 휴식/재시도,
중복 종료, 초기화 상태 제한, 확인 시간 제외 기록, 데모 격리, 저장 표시, 상태 라벨.
영문 경로 캐시 build/comma-gradle-home 및 Android Studio JBR 사용.
실행 결과: :app:assembleDebug 및 :app:testDebugUnitTest 성공.
IDE 테스트 결과: 60 passed, 0 skipped, 0 failed.
1100dp 가로형, 560dp 분할형, 360dp 휴대폰 Compose Preview 렌더링 성공.
실기기 창 밝기/음향 ACK 및 Room 실제 디스크 통합은 이번 JVM 테스트 범위가 아니다.
