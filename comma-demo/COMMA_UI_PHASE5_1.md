# 쉼표 5-1단계 예외 화면과 상태

## Figma 확인 범위
대상 파일 N9WB8GDA5z0UxwcfzqUeVa / 페이지 92:74722.
대상 프레임:
- 미지원 80:57551 / 87:68617
- 제어 실패 79:54177 / 88:72273
- 최초 안내 80:56418 / 87:66762
- 주차 아님 80:57033 / 85:65258
- 주행 종료 122:42090

80:57551의 get_design_context/get_screenshot 실제 호출에서 공통 View seat 한도 오류 확인.
페이지 get_metadata도 같은 오류. 현재 캐시에 각 대상 프레임의 상세 메타데이터/이미지는 없었다.
공통 한도가 확인된 후 나머지 동일 파일 호출은 반복하지 않았다.
따라서 이번 화면은 확인된 기존 쉼표 디자인 시스템 기반 기능 중심 임시 UI다.
각 예외 화면의 원본 배치/아이콘/컬러/텍스트와 일치한다고 주장하지 않는다.
900dp 이상 2열, 그 미만 세로 스크롤; 기존 시스템 글꼴과 대체 Canvas 아이콘 재사용.
원본 Figma 대조는 한도 해제 후 별도 작업이다.

## 실제 제공자와 상태
현재 root는 MockVehicleRepository이며 실제 SDK는 없다.
기존 Mock의 parked=true 고정을 제거했다. 기본 parked=null -> UNKNOWN.
명시적 true -> PARKED / false -> NOT_PARKED. 실내외 온도/배터리는 모의값임을 표시한다.
VehicleCapabilities는 공조와 주행 신호 지원을 SUPPORTED/UNSUPPORTED/UNKNOWN으로 구분.
Mock의 공조·주행 신호는 UNSUPPORTED다.

VehicleControlState는 요청 없음/대기/성공/실패/미지원/확인 불가를 구분한다.
성공은 isMock=false 및 SUPPORTED인 저장소의 Applied 응답에서만 인정한다.
명시적 실패 응답은 FAILURE, 예외·응답 없음은 UNKNOWN, Unavailable은 UNSUPPORTED다.
Mock Applied도 실제 성공으로 표시하지 않는다.
현재 Mock에서는 실제 제어 요청을 보내지 않고 미지원 화면으로 연결한다.
실패/확인 불가 화면은 실제 어댑터가 응답을 제공할 때 사용하며 Preview와 단위 테스트로 검증한다.

## 최초 사용과 권한
Manifest와 현재 기능에는 Android 런타임 권한이 없다.
앱 창 밝기(시스템 전체 설정 아님), 앱 배경색, ToneGenerator, 앱 내부 Room 사용이다.
존재하지 않는 Pleos/Android 권한, 위치/마이크/알림/차량 권한 요청을 추가하지 않았다.
최초 화면의 안내 확인은 정보 확인일 뿐 Android 권한 허용이 아니다.
안내 확인만 쉼표 전용 SharedPreferences에 저장한다.
향후 실제 어댑터 연결 시 해당 SDK의 실제 권한 계약을 확인하고 정식 요청 흐름을 추가해야 한다.

## 휴식 시작과 사용자 확인
실제 연결은 PARKED가 확인돼야 새 휴식을 시작한다. UNKNOWN을 사용자 입력으로 덮어쓰지 않는다.
Mock 환경은 사용자가 직접 주차 확인 후 앱 내부 휴식을 사용할 수 있다.
이 확인은 localParkedConfirmed 별도 필드이며 VehicleState는 UNKNOWN으로 유지한다.
새 휴식 준비 시 사용자 확인을 초기화한다. 명시적 NOT_PARKED면 시작하지 못한다.
최초 안내 미확인은 startIssue로 다시 차단해 화면을 닫아도 안내 확인을 우회하지 못한다.
설정 상단 연동/주차 상태 확인에서 접근한다. 예외 안내는 RestPhase를 바꾸지 않는다.
돌아가기/앱 내부 계속/현재 주차 다시 확인/제어 재요청/완료 보기 액션을 현재 예외에 맞게 제한한다.

## 주행 종료의 연결 범위
DrivingStatus.DRIVING은 NOT_PARKED와 별개다. 주차 아님만으로 주행을 추측하지 않는다.
실제 제공자, SUPPORTED 주행 신호, DRIVING, 비데모, 활성 세션이 모두 충족될 때만
dispatchDrivingStop -> 기존 RestSessionLifecycle.finishForDriving -> manager.finish -> 복구/저장 큐를 사용한다.
EndReason.DRIVING을 문자열 기록 사유에 추가했다. Room 스키마 변경은 없다.
기존 deadline 이후 TIMER 사유 정책은 유지한다.
반복 신호/이미 종료된 세션은 중복 종료·기록 제출하지 않는다.
현재 Mock에서는 이 경로가 실행되지 않으며 UI에 자동 종료 미지원/실차 검증 필요를 표시한다.
실제 주행 신호 수신·신뢰성·권한·실차 안전성은 아직 검증하지 않았다.

## 보존 및 한계
기존 READY/RESTING/WAKING/FINISHED, 30초, 5/10분, 복구 결과, 앱 범위 큐/재시도, 데모 보존.
신규 실제 차량 API/자연음/알람 전용 모드/차박/전역 테마/Android 권한 추가 없음.
주차 UNKNOWN 중 활성 휴식을 임의로 종료하지 않는다.
복구 결과는 기존처럼 앱 내부 상태 확인과 기기 ACK 확인 불가, 차량 미지원으로 구분한다.

## 변경 파일
app/src/main/java/com/naru/app/comma/:
- VehicleExceptionState.kt (신규 모델과 정책)
- CommaExceptionScreen.kt (신규 반응형 예외/상태/Preview)
- VehicleRepository.kt
- RestUiState.kt
- RestViewModel.kt
- RestSessionLifecycle.kt
- RestSessionManager.kt
- CommaRoute.kt
- CommaSettingsScreen.kt
- CommaScreen.kt
- CommaFinishedScreen.kt
app/src/test/java/com/naru/app/comma/VehicleExceptionTest.kt (신규)
COMMA_UI_PHASE5_1.md

## 검증
기존 60개를 삭제하지 않고 신규 VehicleExceptionTest 14개 추가 (예상 총 74개).
주차 3상태/Mock 사용자 확인 분리/실제 UNKNOWN 우회 차단/최초 안내/제어 4결과/
Mock 성공 차단/실제 신호 조건/UNKNOWN 비추론/중복 종료·저장/액션 제한/재진입을 검증한다.
테스트의 주행 신호와 저장소는 fake이며 실제 차량 제어/실차 검증 결과가 아니다.
Gradle은 기존 영문 경로 build/comma-gradle-home와 Android Studio JBR 사용.
