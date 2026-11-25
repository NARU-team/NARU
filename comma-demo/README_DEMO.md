# NARU 쉼표 촬영 안내

## 실행할 프로젝트
저장소 https://github.com/NARU-team/NARU.git
촬영용 제안 브랜치 demo/comma-submission-20261115 (사용자가 생성/업로드해야 함)

원격 main은 ai.naru.app 팀 앱이고 현재 로컬은 com.naru.app 독립 프로젝트다.
차박/팀원 설정을 덮어쓰지 않기 위해 새 브랜치의 **comma-demo/** 안에만 촬영 프로젝트를 추가한다.
Android Studio로 저장소 루트가 아닌 comma-demo를 연다.

## clone / Sync / Run
```powershell
git clone --branch demo/comma-submission-20261115 https://github.com/NARU-team/NARU.git NARU-demo
cd NARU-demo/comma-demo
./gradlew.bat --gradle-user-home C:/NARUGradleCache -PcommaDemoMode=true :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
```

1. Android Studio → Open → NARU-demo/comma-demo.
2. SDK Manager → Android SDK Platform 37 설치. 실행 기기는 Android 9(API 28) 이상.
3. Gradle Sync. 기존 AGP 9.5.1 / Gradle 9.6.0 / Kotlin 2.2.10 유지.
4. 저장소 Gradle JVM criteria는 Java 25이다. 호환 Android Studio/Gradle JVM과 Foojay toolchain 다운로드 네트워크를 준비한다.
5. Build Variants → app **debug**, 실행 가능한 팀원 에뮬레이터 또는 USB 디버깅 기기를 선택하고 Run.
6. Debug는 DEMO 진입 버튼 기본 활성화. 위 플래그를 명시하면 동일하게 재현된다.
7. 일반 Debug에서 도구 숨김: -PcommaDemoMode=false. Release DEMO_MODE는 항상 false.
8. macOS/Linux는 ./gradlew. 실행 권한이 없으면 chmod +x gradlew.

APK app/build/outputs/apk/debug/app-debug.apk.
```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.naru.app/.MainActivity
```
local.properties는 각 PC SDK 경로를 Android Studio에서 생성한다. 커밋하지 않는다.
루트 팀 앱의 wrapper가 아니라 comma-demo 안의 wrapper를 사용한다.

## 주 촬영 순서
| 순서 | 누를 버튼 | 화면/확인 |
|---|---|---|
| 1 | 홈 쉼표 모드 시작하기 | 설정/최초 안내 |
| 2 | 촬영 DEMO · 설정부터 시작 | READY, DEMO 가상 차량/실제 제어 아님/Room 제외 |
| 3 | 15분, 밝기20%, 조명15%, 무음 선택 | 선택값 카드 반영 |
| 4 | 공조 카드 → 이 설정으로 쉬기 | RESTING, 가상 공조24°C/조명15% 적용 |
| 5 | 종료 30초 전 | 00:30, 실제 잠깐 기다리면 밝기 변화 |
| 6 | 종료 시점으로 | WAKING, 기존 30초 확인, 짧은 단발 톤 |
| 7 | 5분 더 | RESTING, 약5분/계획20분/원형비율 갱신 |
| 8 | 종료 시점으로 → 휴식 종료 | FINISHED, 가상 시간/복구, 실제 Room 저장 제외 |
| 9 | 새로운 휴식 설정 | DEMO READY. 10분 연장도 같은 경로 |

WAKING의 '확인 시간 만료'는 기다림 없이 기존 자동 종료/복구를 시연한다.
시간 점프는 별도 데모 시계만 바꾸며 production 시계/타이머 알고리즘을 바꾸지 않는다.
가로 촬영 권장. 좁은 화면은 스크롤해 아래 버튼에 접근한다.

## 알람 전용 / 예외
- 다시 촬영 · 초기화 → 설정값 변경 → **알람만 맞추기**.
- '알람 전용(설정 변경 없음)' 확인: 앱 밝기/음향 및 가상 공조/조명은 선택 설정을 적용하지 않는다.
- 종료 시점으로 → 알람 → 종료/확인 시간 만료.
- DEMO 앱 내 타이머만 지원. 일반 모드 알람 전용/시스템 알람/백그라운드 정시 알림은 미구현.
- '예외/차량 시연'을 펼치면 최초 안내/UNKNOWN/주차 아님/미지원/실패/가상 주행 버튼이 있다.
- UNKNOWN·주차 아님은 '가상 주차로 복귀'로 실제 UI의 가상 상태가 바뀐다.
- 가상 실패 → 제어 다시 요청은 가상 실패를 해제하며 실제 API 호출 없음.
- 가상 주행은 먼저 휴식을 시작한 뒤 누른다. DEMO만 종료/복구 → 완료 보기.
- 장면 사이 '다시 촬영 · 초기화'. 이 버튼은 실제 기록/저장 큐에 영향 없음.

## 고정 가상 데이터
초기 PARKED / 실내28 / 실외32 / 배터리75 / 공조목표24.
가상 쾌적 프리셋: 실내23 / 실외22 / 배터리80 / 목표23.
가상 더운 날 프리셋: 초기 28/32/75/24 값.
랜덤/배터리 예측이 아니라 명시적 버튼으로 고르는 가상 값이다.
종료 시 공조/조명만 시작 전 상태로 복구한다. 주차/온도/배터리 신호를 복구로 조작하지 않는다.
가상 주행 후 NOT_PARKED 유지. 전체 초기화 버튼으로 PARKED 복귀.
실제 차량 SDK/실제 안전 기능이 아니다.

## Room 촬영은 별도 일반 모드
DEMO 나가기 → 연동/주차 상태 확인 → 필요 시 안내 확인 →
다시 상태 확인 → 직접 주차 확인(앱만) → 휴식 시작 → 2~3초 후 수동 종료.
완료 화면에서 실제 경과 초와 저장 확인을 촬영한다. 대기/실패면 다시 저장/조회.
가상 기록은 실제 Room에 넣지 않는다. 정상 모드도 차량은 Mock이며 실제 Room은 앱 내부 기록만 뜻한다.

## 검증 범위 / 미구현
개발 PC에서는 실제 앱 실행/오디오/창 밝기/녹화/실차를 검증하지 못했다.
빌드/JVM 테스트/Preview와 UI 테스트 APK 컴파일을 기기 실행과 구분한다.
기기 UI 테스트:
```powershell
./gradlew.bat --gradle-user-home C:/NARUGradleCache -PcommaDemoMode=true :app:connectedDebugAndroidTest
```
자연음 선택·미리 듣기는 **P1 미구현**. 현재 무음/15초 간격 짧은 톤과 단발 종료 톤만 있다.
미디어 음량과 녹화 오디오 설정은 촬영 기기에서 확인한다.
차량 실제 제어/실차 자동 종료/배터리 예측/온도변화 측정/피드백/시스템 알림은 미구현.
Figma 호출 한도로 일부 화면은 기존 디자인 시스템 기반 임시 UI, 시스템 글꼴/대체 아이콘 사용.
별도 기획서 파일은 로컬에 없었다. 원격 README의 시간선택→환경설정→알람→복구 흐름만 추가 확인했다.

## 오류 대처
- DEMO 없음: debug 선택 후 -PcommaDemoMode=true로 빌드/재설치.
- 시작 비활성: 가상 주차 상태 확인 또는 다시 촬영 · 초기화.
- WAKING 사라짐: 기존 30초 만료. 다시 촬영하고 시간 안에 연장/종료.
- GradleWorkerMain/캐시 오류: 영문 경로 C:/NARUGradleCache 사용.
- Sync 실패: SDK37/호환 Gradle JVM 및 toolchain 다운로드 네트워크 확인.
- Preview NoSuchMethodError: IDE Build/Rebuild 후 새로고침.
- 진행 중 세션/미저장 큐는 강제 종료/재부팅 복구 미지원. 실제 기록은 저장 완료 확인 후 종료.
- 에뮬레이터 오류 해결은 하지 않았으며 실행 가능한 팀원 PC/실기기를 사용한다.

## Git 전달
로컬에는 .git/Git CLI가 없었다. GitHub API 쓰기/브랜치 생성이 403으로 거절돼 원격 변경은 하지 않았다. 사용자가 원격 main 기반 새 브랜치를 만들고 comma-demo/에 업로드한다.
변경은 comma-demo/만 추가, main/root 앱/차박/전역 테마는 보존.
업로드는 명시적 파일 목록만 사용하며 local.properties/.env/키/토큰/.idea/.gradle/build/APK는 제외.
현재 로컬의 gradle/wrapper/ 폴더(JAR와 properties)를 그대로 복사한다. 원격 업로드/브랜치 생성은 수행되지 않았다.
팀원은 자기 수정 브랜치를 만들고 명시적 파일만 stage, diff 확인 후 push. force push/자동 merge 없음.

## 사용자 업로드 절차
이 개발 폴더의 .git 부재 때문에 원격 root를 현재 폴더로 덮어쓰지 않는다.
별도 폴더에 NARU-team/NARU를 clone하고 최신 main에서 새 브랜치를 만든다:
```powershell
git switch main
git pull --ff-only
git switch -c demo/comma-submission-20261115
```
새 comma-demo/ 폴더에 현재 독립 프로젝트의 app/src/, app/build.gradle.kts, app/.gitignore,
build.gradle.kts, settings.gradle.kts, gradle.properties, gradlew, gradlew.bat,
gradle/libs.versions.toml, gradle/gradle-daemon-jvm.properties, gradle/wrapper/,
.gitignore, README_DEMO.md, COMMA_*.md만 복사한다.
local.properties, .idea, .gradle, build, 서명 키/.env는 복사하지 않는다.
새 폴더의 .gitignore로 local.properties/.gradle/build/.env/키를 제외한 후:
```powershell
git add -- comma-demo
git diff --cached --check
git diff --cached --name-only
git commit -m "Add isolated Comma filming demo"
git push -u origin demo/comma-submission-20261115
```
목록에 민감정보/생성물이 보이면 commit하지 말고 해당 파일만 stage에서 제외한다.
기존 main/root 앱/차박 파일이 변경되지 않았는지 확인한다. force push/자동 merge 금지.
업로드 후 팀원이 위 clone 명령을 실행한다.

## 변경 파일
신규: DemoVehicleState.kt, CommaDemoControls.kt, DemoSubmissionPreviews.kt,
DemoSubmissionTest.kt, androidTest/CommaDemoFlowTest.kt, README_DEMO.md, COMMA_SCENARIO_AUDIT.md.
수정: DemoSessionController.kt, RestViewModel.kt, RestUiState.kt, CommaRoute.kt,
RestingPresentation.kt, CommaSettingsScreen.kt, CommaExceptionScreen.kt,
CommaFinishedScreen.kt, CommaRestingScreen.kt, CommaWakingScreen.kt,
CabinEffects.kt, RestingPresentationTest.kt, app/build.gradle.kts, .gitignore.
production RestSessionManager/RestClock 알고리즘, Room 큐, 차박 및 전역 테마는 이번 제출 작업에서 변경하지 않았다.
과거 COMMA_UI_PHASE 문서는 당시 단계의 기록이며 최신 촬영 절차는 이 README를 우선한다.

## 촬영 체크리스트
- [ ] 팀원 기기에서 설치/실행/음량/녹화 확인 (개발 PC에서는 실행하지 못함)
- [ ] 홈→DEMO 설정→15분→가상 공조 설정 완료→이 설정으로 쉬기
- [ ] RESTING→종료 시점으로→WAKING→5분 더→RESTING→다시 알람→종료→FINISHED
- [ ] 확인 시간 만료로 자동 종료/복구
- [ ] 알람 전용 시작 시 설정 변경 없음
- [ ] UNKNOWN/주차 아님/가상 실패/미지원/가상 주행 장면과 복귀
- [ ] 실제 Room 기록은 DEMO를 나가 별도 일반 앱 휴식으로 확인
- [ ] 실제 차량 연동 성공/실차 안전 기능으로 설명하지 않기

## 이번 검증 결과
DEMO true: assembleDebug/testDebugUnitTest/assembleDebugAndroidTest 성공.
DEMO false: assembleDebug/testDebugUnitTest 성공.
JVM 테스트는 기존 74 + 새 DEMO 9 = 83 passed / 0 skipped / 0 failed.
DEMO 설정 가로형/분할형 및 알람 Compose Preview 렌더링 성공. UI 테스트는 APK 컴파일만 했고 기기 실행/영상 녹화는 미확인이다.
현재 app/build/outputs/apk/debug/app-debug.apk는 DEMO_MODE=true로 최종 빌드했다.
