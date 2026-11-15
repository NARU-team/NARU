# 쉼표 모드 MVP

차박 모드 및 실제 차량 SDK는 이번 개선 범위에서 변경하지 않았습니다.
기존 Material 3 임시 화면과 15/30/45분·직접 입력 기능을 유지합니다.

## 실행과 확인
진입 → 시간·밝기·앱 무드 조명·톤 설정 → 휴식 시작 순서입니다.
- 실제 시간은 단조 시계 elapsedRealtime 종료 시점으로 계산합니다.
- 마지막 30초에 밝기가 점차 증가합니다.
- 0초에 앱 대화상자와 짧은 톤을 알리고 30초 확인 시간을 시작합니다.
- 확인 시간은 [종료 시점, 종료 시점 + 30초) 구간입니다. 정확히 30초가 되면 연장할 수 없습니다.
- 확인 시간 안의 +5/+10분은 누른 시점부터 해당 시간을 연장합니다.
- 휴식 중 +5/+10분은 기존 종료 시점에 해당 시간을 더합니다.
- 종료 예정 시각은 확인 시간 동안 고정됩니다. 기기 시각 변경 시 표시 시각은 새 시각에 맞춰 계산합니다.
- 확인 시간이 지나면 자동 종료·설정 복구·기록 저장 큐 전달을 수행합니다.
- 종료 시간 뒤의 확인 대기 시간은 자동 종료 기록의 실제 휴식 시간에서 제외합니다.
- 연장 포함 계획 시간과 다음 세션의 기본 선택 시간을 구분합니다.
- 무드 조명은 앱 배경색, 밝기는 Activity 창, 사운드는 15초 간격의 짧은 톤입니다.
- 시스템 밝기·음량·차량 실내등을 변경하지 않습니다.
- 종료 알림은 앱 대화상자와 톤이며 시스템 알림이 아닙니다.

## 종료·수명·저장 정책
### 명시적 종료
사용자가 즉시 종료 또는 나가기 확인을 누르면 설정을 복구하고 MANUAL 기록을 앱 범위 저장 큐에 전달합니다.
이미 타이머 종료 시점을 지났다면 TIMER로 처리합니다.
같은 세션의 종료 처리는 한 번만 수행하며 Room 기본 키로 중복 행을 방지합니다.
큐 전달과 Room 저장 성공은 다릅니다. 화면은 미저장 수와 오류, 다시 저장/조회를 표시합니다.

### ViewModel 정리
onCleared는 ViewModelScope가 취소된 상태에서도 RestSessionLifecycle.close를 동기적으로 호출합니다.
진행 중 세션을 종료하고 설정을 복구한 뒤, 반환된 기록을 RestHistoryStore.enqueue에 전달합니다.
종료 시점 전의 정리는 VIEWMODEL_CLEARED(화면 수명 종료), 이미 종료 시점이 지났다면 TIMER입니다.
enqueue는 실행 스코프와 별개로 먼저 대기 목록을 갱신하고 앱 범위 코루틴에서 저장합니다.
따라서 ViewModelScope 취소가 제출한 저장 작업을 취소하지 않습니다.
Room과 저장 스코프는 RestApplicationServices가 프로세스 범위로 보유하며 ViewModel에서 Room.close를 호출하지 않습니다.
DB I/O는 repository의 IO dispatcher에서 수행합니다. UI 스레드에서 저장 완료를 기다리지 않습니다.
조회 실패와 저장 실패를 구분하며, 저장 성공 뒤 조회가 실패해도 기록을 다시 저장하지 않습니다.

### 회전·백그라운드·강제 종료
회전/크기 변경에는 기존 ViewModel이 유지되며 세션을 종료하지 않습니다.
홈 이동 등 백그라운드 전환만으로 세션을 종료하지 않습니다.
백그라운드 정시 실행과 시스템 알림은 보장하지 않습니다.
OS 프로세스 종료·강제 종료·스와이프 제거에서는 onCleared 호출을 보장할 수 없습니다.
진행 중 세션 및 미저장 큐는 메모리 상태이므로 프로세스 종료 시 소실될 수 있습니다.
프로세스 종료 직전의 저장 완료도 보장하지 않습니다.
이미 Room 저장이 성공한 종료 기록만 재실행 후 유지됩니다.
진행 중 타이머의 프로세스 사망/재부팅 복구, 영속 outbox, Foreground Service는 아직 구현하지 않았습니다.
창 밝기와 화면 켜짐 플래그는 Compose 효과 해제 시 복구하며 시스템 전역 값을 바꾸지 않습니다.

## 개발 전용 DEMO_MODE
기본 Debug와 모든 Release의 DEMO_MODE는 false입니다.
-PcommaDemoMode=true를 명시한 Debug에서만 데모 패널이 표시됩니다.
Release는 같은 속성을 전달해도 false로 고정됩니다. 속성은 전역 설정에 저장하지 않습니다.

데모 빌드 후:
1. 쉼표 모드에 진입하고 원하는 시간·설정을 선택합니다.
2. 개발 전용 DEMO 패널의 데모 시작을 누릅니다. 실제 세션이 진행 중이면 진입할 수 없습니다.
3. 종료 30초 전으로 이동하여 실제 초 경과에 따른 밝기 변화를 관찰합니다.
4. 종료 시점으로 이동하여 종료 대화상자와 확인 시간 카운트다운을 확인합니다.
5. +5분 또는 +10분으로 연장합니다. 종료 시점으로 다시 이동할 수 있습니다.
6. 대화상자의 DEMO: 확인 시간 만료 버튼으로 자동 종료·복구를 즉시 확인합니다.
7. 즉시 종료 또는 데모 나가기로 수동 복구도 확인합니다.

DemoSessionController는 별도 DemoClock, RestSessionManager, CabinController를 보유합니다.
가상 시계만 빨리 진행하며 실제 RestClock이나 실제 세션 종료 시점을 변경하지 않습니다.
타이머 알고리즘 자체에 데모 분기를 추가하지 않았습니다. 같은 타이머 로직을 별도 입력 시계로 시연합니다.
DEMO 표기와 가상 종료 시각을 표시하고, 차량 요청 및 Room 기록 저장 경로를 사용하지 않습니다.
데모 종료 기록은 시연용 세션 안에서만 유지되며 데모를 나가면 실제 기록 목록으로 돌아갑니다.

## UI 구조와 Figma 적용 지점
Figma 화면은 제공되지 않았으며 디자인을 확정하지 않았습니다. 기존 임시 레이아웃을 유지합니다.
- RestUiState: 세션, 실제 적용 중 앱 설정, 차량 상태, 저장 상태, 메시지, 데모 여부, 현재 시각
- RestUiActions: 시간 선택, 설정, 시작/연장/종료, 공조 요청, 저장 재시도
- CommaRoute: 생명주기 기반 상태 수집, 뒤로 가기, 한 번에 하나의 대화상자, 개발 패널
- CommaScreen: 840dp 이상 2열, 좁은 화면 1열/스크롤, 직접 입력 초안 보존
- RestPreparationCard / ClimateRecommendationCard / RestTimerCard / RestHistoryCard: 상태와 콜백만 받는 독립 컴포넌트
- CabinEffects: Activity 창 밝기·화면 켜짐·톤 수명 관리
향후 Figma 적용은 카드/레이아웃을 교체하면서 상태 계약과 타이머·저장 로직을 유지하면 됩니다.

## 빌드
기본 사용자 캐시는 과거 GradleWorkerMain ClassNotFoundException이 있어 영문 경로 캐시를 사용합니다.
PowerShell에서 프로젝트 루트를 작업 디렉터리로 실행합니다.

```powershell
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
./gradlew.bat --gradle-user-home C:/AndroidProjects/NARU/build/comma-gradle-home :app:assembleDebug :app:testDebugUnitTest --console=plain
```

데모 APK:
```powershell
./gradlew.bat --gradle-user-home C:/AndroidProjects/NARU/build/comma-gradle-home -PcommaDemoMode=true :app:assembleDebug --console=plain
```

일반 모드로 돌아가려면 -PcommaDemoMode=true 없이 다시 빌드합니다.
캐시는 build 생성물이며 기존 전역 Gradle 설정과 기존 사용자 캐시를 변경하지 않습니다.
에뮬레이터 작업은 수행하지 않습니다.

## 이번 변경 파일
수정:
- app/build.gradle.kts
- app/src/main/java/com/naru/app/comma/RestSessionManager.kt
- app/src/main/java/com/naru/app/comma/RestViewModel.kt
- app/src/main/java/com/naru/app/comma/CommaScreen.kt
- COMMA_MVP.md

추가:
- app/src/main/java/com/naru/app/comma/RestApplicationServices.kt
- app/src/main/java/com/naru/app/comma/RestHistoryStore.kt
- app/src/main/java/com/naru/app/comma/RestSessionLifecycle.kt
- app/src/main/java/com/naru/app/comma/DemoSessionController.kt
- app/src/main/java/com/naru/app/comma/RestUiState.kt
- app/src/main/java/com/naru/app/comma/CommaRoute.kt
- app/src/main/java/com/naru/app/comma/CabinEffects.kt
- app/src/test/java/com/naru/app/comma/RestTimerRegressionTest.kt
- app/src/test/java/com/naru/app/comma/RestHistoryStoreTest.kt
- app/src/test/java/com/naru/app/comma/DemoSessionControllerTest.kt

## 검증 결과
- 일반 Debug: :app:assembleDebug 및 :app:testDebugUnitTest 성공
- DEMO_MODE 활성 Debug: 같은 두 작업 성공
- 쉼표 모드 기존 11개 + 추가 17개 테스트 통과
- 기본 사용자 캐시를 변경하지 않고 문서의 영문 경로 캐시 사용
- 최종 APK는 데모 속성 없이 일반 모드로 다시 생성
- 기기 배포/에뮬레이터 실행/실차 검증은 수행하지 않음

## 테스트 구성
기존 RestSessionManagerTest 11개는 변경 없이 유지합니다.
추가 17개:
- RestTimerRegressionTest 6개: 고정 종료 시각, 확인 시간 경계, 확인 중 수동 종료, 명시적 정리 사유, 다음 세션 선택/기록 ID
- RestHistoryStoreTest 6개: 호출자 취소 중 저장, 중복 정리, 수동 종료 후 정리, 저장 실패 재시도, 조회 실패, 연속 제출
- DemoSessionControllerTest 5개: 실제 시계·세션 격리, 밝기/종료/복구, 두 연장, 잘못된 상태의 이동, 재시작
Room 실제 디스크 통합 및 기기 UI·음향·창 밝기는 이 JVM 회귀 테스트의 검증 범위가 아닙니다.
