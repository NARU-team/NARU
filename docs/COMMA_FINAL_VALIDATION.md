# 쉼표 모드 최종 검증

기준일: 2026-11-26

## 구현 결과

- Figma v.3 설정/적용/소리/직접설정/미지원/휴식/알람 흐름 유지
- Room v6 진행 세션·피드백·6개 복구 결과·충전 관측 품질 보존
- AlarmManager, 자연음/알람 오디오 조정, 5/10분 연장
- capability 기반 공조·조명 적용과 재조회·복구
- 공조 OFF 의존성 대응: 전원 백업→ON→ON 재조회→목표 온도 백업→적용→재조회, 실패 시 원래 OFF 상태 복구
- 독립 쉼표 LLM endpoint, 복합 구조화 제안, LOCAL_RULE fallback
- 직접 온도, 무음, 화면, 앰비언트 조명, 연장 제안
- 요청 ID/단계 기반 늦은 응답 폐기와 승인 시 재검증
- Pleos ON_DEVICE STT 입력 UI 연결
- Gleo schema/전용 Activity/3개 action filter/callback sender와 기존 승인 제안 연결
- 기존 CarPropertyManager를 공유하는 쉼표 read-only 충전 source와 세션 tracker 연결

## 실제 검증 결과

| 검증 | 결과 |
| --- | --- |
| Android JVM 테스트 | 328 passed |
| Debug APK | 성공 |
| AndroidTest APK | 성공 |
| 서버 테스트 | 79 passed |
| 서버 typecheck | 성공 |
| Pleos AVD cold boot | 성공 |
| Debug APK 설치/실행 | 성공 |
| 쉼표 설정 화면 실렌더 | 성공 |
| connectedDebugAndroidTest | 6 discovered / 6 executed / 6 passed |
| Room v1/v2/v3/v4/v5→v6 | 실제 Pleos AVD 계측 통과 |
| 실제 OpenAI 호출 | 미실행 |
| ADB Gleo Action Intent | cold start Activity 수신→안전 onboarding→쉼표 설정→승인 dialog 자동 진입, 취소 후 비반복 확인. 실제 Gleo AI 아님 |
| Gleo schema APK 포함 | `assets/schema.json` 2,674 bytes 확인 |
| 실제 Gleo 호출 | schema 미심사로 미실행 |
| 충전 property | Pleos AVD에서 포트 연결만 지원(false), 충전 상태/Wh는 미지원 |
| 차량 공조/조명 전체 시나리오 | PARTIAL — Virtual Console 활성화 전 HVAC DDS 비활성 원인 확인. 활성화 후 23.5°C 요청이 500ms에는 미반영, 3초 후 실제 재조회 23.5°C 확인 |

## Pleos HVAC 최종 진단

- `HVAC_POWER_ON`: read/write, area `0x75`; `HVAC_TEMPERATURE_SET`: read/write, driver/passenger `0x1/0x4`, 15~30°C; `HVAC_FAN_SPEED`: read/write, area `0x5`.
- Android 공식 계약상 전원이 false이면 온도·팬 같은 종속 속성이 unavailable일 수 있다.
- Virtual Console 웹 클라이언트가 없을 때 HVAC DDS publisher/subscriber가 비활성화되어 Pleos 기본 UI와 나루 앱 모두 전원을 실제로 켜지 못했다.
- 웹 클라이언트 활성 후 조회는 정상화됐다. SDK set 콜백 직후에는 이전 값이 유지됐고 약 3초 후 actual 값이 반영됐다. 쉼표 어댑터는 최대 3초의 제한적 재조회 후에만 성공 처리하며, 실패 시 “온도 변경 없이 계속” fallback을 유지한다.
- 앱의 직접 AAOS HVAC 접근은 signature/privileged `android.car.permission.CONTROL_CAR_CLIMATE`가 없어 차단되며, 생산 경로는 정상적으로 Pleos SDK의 `pleos.car.permission.*` 계약을 사용한다. 공용 gateway와 Manifest는 변경하지 않았다.

## Figma 비교

실제 Figma 스크린샷 8개와 기존 구현을 비교했다. 종료 10:93334와 제어 실패 13:133405는 Starter 호출 한도로 조회하지 못했다. Pleos Emulator에서는 실제 시스템 영역과 앱 viewport, 홈, 연동 안내, 설정 화면을 확인했다. 원본 Figma 아이콘/로고 에셋은 확인되지 않아 픽셀 일치 완료로 처리하지 않는다.

## 실행 방법

1. local.properties에 배포 서버가 있을 때만 naru.serverUrl을 설정한다.
2. ./gradlew :app:testDebugUnitTest
3. ./gradlew :app:assembleDebug
4. Pleos AVD Automotive_Ultrawide를 cold boot한다.
5. adb install -r app/build/outputs/apk/debug/app-debug.apk
6. ai.naru.app/.MainActivity 실행 후 쉼표 모드로 진입한다.
7. 서버는 server에서 npm ci, npm test, npm run typecheck로 검증한다.

SERVER_URL이 비어 있거나 서버가 실패하면 LOCAL_RULE로 동작한다. OPENAI_API_KEY는 서버 환경에만 두며 Android 앱에 넣지 않는다.

## 이번 수동 UI 최적화

- 쉼표 상단 차량 상태를 한 줄로 축약했다.
- 제안/시간 영역과 2×2 환경 카드가 Ultrawide 앱 viewport를 더 효율적으로 사용한다.
- 자연어 FAB 겹침을 제거하고 설정 카드의 명시 버튼으로 유지했다.
- 차량 실패 안내는 내부 예외 대신 사용자 문구를 표시하며 재시도와 온도 변경 없이 계속을 제공한다.
- 완료 화면은 핵심 요약과 확인 필요 개수를 먼저 표시하고 복구 상세를 펼쳐본다.
- 일반 모드에서는 촬영 DEMO 진입, 정상 저장 후 재저장/조회, 알람 전용 미지원 버튼을 숨긴다.
- 홈 좌하단 `개발용`은 공용 `MainActivity`의 차박 디버그 패널이므로 승인되지 않은 공용 변경을 피하기 위해 유지했다.

## 남은 제한 사항

- Pleos Playground schema 제출·심사와 실제 Gleo AI App 검증 (USER_ACTION_REQUIRED)
- 외부 음악 앱 대상 및 Intent 정책
- EV_CHARGE_STATE를 지원하는 실차 또는 Pleos 이미지의 충전 시작/종료 검증
- NaviHelper 의존성·권한 승인
- Pleos 차량 제어, 알람, 프로세스/재부팅 복구 전체 수동 검증
- Figma 종료/실패 화면과 원본 에셋 검증

## 이번 작업의 신규/수정 범위

- app/src/main/java/ai/naru/app/comma/CommaIntentRepository.kt
- app/src/main/java/ai/naru/app/comma/CommaIntentRules.kt
- app/src/main/java/ai/naru/app/comma/RestViewModel.kt
- app/src/main/java/ai/naru/app/comma/RestUiState.kt
- app/src/main/java/ai/naru/app/comma/CommaSettingsScreen.kt
- app/src/test/java/ai/naru/app/comma/CommaIntentRulesTest.kt
- server/lib/commaIntent.ts
- server/api/comma/intent.ts
- server/test/commaIntent.test.ts
- 쉼표 관련 문서

승인 범위에서 MainActivity에는 만료·중복·차박 활성 상태를 차단하는 쉼표 전용 Gleo navigation hook만 추가했고, Gradle에는 AndroidX Test/Room testing·runner·v6 schema export만 추가했다. 차박 화면·세션·navigation 분기는 변경하지 않았다. Manifest의 read 권한/Gleo Activity와 기존 CarPropertyManager 읽기 전용 주입은 유지했으며 연결 소유권과 해제 동작은 변경하지 않았다. 작업 시작 전 변경은 유지했고 커밋·push·merge는 수행하지 않았다.
