# 쉼표 모드 테스트 보고서

검증일: 2026-11-26

## 실행 결과

| 명령 | 결과 |
| --- | --- |
| `./gradlew :app:testDebugUnitTest` | 성공, 328 passed, 0 failed |
| `./gradlew :app:assembleDebug` | 성공 |
| `./gradlew :app:assembleDebugAndroidTest` | 성공 |
| `./gradlew :app:connectedDebugAndroidTest` | 성공, 6 discovered / 6 executed / 6 passed / 0 failed |
| `npm test` (server) | 성공, 79 passed, 0 failed |
| `npm run typecheck` (server) | 성공 |
| Pleos Connect Emulator 부팅·설치·실행 | 성공, v2.1.0/API 34 |
| Room v1/v2/v3/v4/v5→v6 Migration 계측 | VERIFIED, 각 시작 버전 1개씩 총 5개 통과 |

Gleo 만료·차박 활성 navigation, 외부 envelope 검증, 포트 전용 제한 품질과 HIGH/LIMITED 비혼합 예측 테스트를 추가했고, 공조 OFF 상태 및 SDK 콜백 뒤 지연 반영을 재조회하는 회귀 테스트를 추가해 총 328개다.

## 이번 검증 범위

- 기존 차박/쉼표 전체 JVM 회귀 328개
- 충전 중·포트 연결·UNKNOWN·관측 공백·확인된 비충전 품질 분류
- 충전 관측 불완전 기록과 충전/포트 연결 기록의 에너지 예측 제외
- Gleo action의 승인 제안 변환, 단계/capability/연장 범위 검증
- 쉼표 전용 `POST /api/comma/intent` 요청·응답·단계·capability 검증
- 직접 온도, 무음, 복합 요청, 5/10분 연장 로컬 fallback
- LLM 서버/LOCAL_RULE 처리 출처 구분
- 승인 시점 단계·capability 재검증과 늦은 응답 폐기
- Pleos STT 입력 상태 및 텍스트 fallback 연결 컴파일
- Figma v.3 설정 화면 실제 Pleos viewport 렌더링
- 서버 전체 79개 테스트와 TypeScript typecheck

## Pleos Emulator 실제 확인

`Automotive_Ultrawide` AVD의 `system-images/android-34/pleos_car_emulator`와 태그 `Pleos Connect v2.1.0 APIs`를 확인했다. 데이터 삭제 없이 cold boot했고 Debug APK 설치와 MainActivity 실행에 성공했다.

실제 시스템 상태바, driving view, GNB와 앱 영역이 분리되는 것을 확인했고 쉼표 설정 화면까지 진입했다. 화면은 앱 viewport에서 표시되고 스크롤 가능한 하단 콘텐츠는 초기 viewport 아래에 위치한다.

차량 공조·조명 실제 반영, 알람 대기, 프로세스 강제 종료/재부팅 복구의 전체 수동 시나리오는 이번 실행에서 완료하지 못했으므로 UNVERIFIED다.

## 계측 테스트 해석

`app/src/androidTest`의 5개 migration 테스트와 HVAC 런타임 진단 1개가 Pleos Automotive AVD(API 34)에서 실제 발견·실행됐다. Git 기준 커밋의 v1 엔티티 정의로 DB를 만든 뒤 실제 `MIGRATION_1_2`~`MIGRATION_5_6` 객체를 적용해 각 v1/v2/v3/v4/v5 fixture를 구성했다. 현재 스키마를 과거 스키마라고 복제하지 않았다. 기록·버전별 피드백·활성 세션 백업·v6 UNKNOWN 기본값·중복 종료 INSERT 시 피드백 보존을 검증했다. Room schema export는 v6부터 `app/schemas`에 저장한다.

## Figma 검증

8개 화면은 기존 Figma MCP 스크린샷과 비교했다. 종료(10:93334), 제어 실패(13:133405)는 Starter 호출 한도가 계속되어 추가 조회하지 못했다. Pleos 에뮬레이터에서는 홈·연동 안내·설정 화면을 실제 확인했다. 원본 아이콘/로고 자산은 다운로드하지 못해 픽셀 일치를 주장하지 않는다.

## 미검증/제약

- ADB cold start Action은 MainActivity 쉼표 설정과 Gleo 승인 dialog까지 자동 진입 확인. APK의 assets/schema.json 포함도 확인했으나 Pleos schema 심사와 실제 Gleo AI 호출은 미검증
- Pleos AVD 실측: EV_CHARGE_PORT_CONNECTED만 지원, 초기 false. EV_CHARGE_STATE·charge rate·battery Wh·usable capacity는 미지원
- 이전 headless AVD의 ai.umos.drivingview SIGSEGV와 별개로 이번 GUI `-gpu host` cold boot는 성공했고 NARU 승인 dialog까지 확인. 차량 제어 전체 수동 시나리오는 미완료
- 외부 음악 앱 대상/공식 Intent 미확정
- NaviHelper 의존성과 권한 미추가
- CAR_ENERGY 권한은 ADB grant 성공했고 기존 CarPropertyManager 주입·포트 구독은 연결됨. AVD가 충전 상태 property를 제공하지 않아 실제 충전 시작/종료는 UNKNOWN
- 실제 OpenAI 호출은 API 키/배포 서버가 없어 미실행
- Node 22 요구 프로젝트를 로컬 Node 25에서 실행했으며 테스트/typecheck는 성공

## 2026-11-26 실제 수동 재검증

- 사용자가 제공한 실제 Pleos 화면에서 공조 OFF 상태의 `HVAC_TEMPERATURE_SET` vendor error와 목표 온도 복구 확인 불가를 확인했다.
- AVD `car_service` config에서 `HVAC_POWER_ON`은 read/write area `0x75`, `HVAC_TEMPERATURE_SET`은 read/write driver/passenger area `0x1/0x4`, 범위 15~30°C임을 확인했다. 기존 driver area `0x1` 선택은 config와 일치한다.
- 쉼표 어댑터를 전원 상태 백업 → 필요 시 ON 요청 → ON 재조회 → 목표 온도 백업 → 목표 온도 설정 → 재조회 순서로 수정했다.
- 새 APK를 Pleos GUI AVD에 배포했고 홈과 RESTING 복구 화면, 실제 앱 viewport를 확인했다.
- RESTING 화면의 타이머·차량 카드 렌더링을 확인했다.
- 공식 절차대로 `adb forward tcp:8088 tcp:8088` 후 호스트 브라우저에서 Virtual Console을 열기 전에는 HVAC DDS publisher/subscriber가 모두 비활성이고 `PUT /vehicle/hvac`가 `503 emulator_inactive`였다.
- Virtual Console 브라우저를 연 뒤 publisher/subscriber가 활성화되어 SDK 전원·온도 조회가 가능해졌다. 전원은 `true`, 목표 온도는 `23.0°C`로 조회됐다.
- SDK `setHvacTemperature(23.5°C)` 콜백 뒤 500ms 재조회는 이전 값 `23.0°C`였지만 3초 후에는 `23.5°C`가 확인됐다. 쉼표 어댑터는 최대 3초 동안 제한적으로 재조회하며, 그 뒤에도 불일치하면 성공으로 표시하지 않는다.
- 기존 `PleosVehicleGateway`의 driver area `0x1` 및 capability 기반 power area 선택은 config와 일치하므로 공용 gateway 계약은 변경하지 않았다.
- 설정 Compose Preview에서 추천 카드, 2×2 환경 카드, 축소된 시간 카드가 겹치지 않는 것을 확인했다.
- 일반 쉼표 화면의 촬영 DEMO 진입, 재저장/조회, 겹치는 자연어 FAB를 제거했다. 홈 좌하단 공용 개발 패널은 차박 공용 UI라 수정하지 않았다.
- `:app:connectedDebugAndroidTest`는 6 discovered / 6 executed / 6 passed다. HVAC 진단 테스트 통과는 진단 코드 실행 성공을 뜻하며, 23.5°C 실제 반영 성공을 뜻하지 않는다.

## 안전 회귀

- 이번 작업에서 차박 파일과 VehicleGateway 메소드 계약 변경 없음
- 승인 범위에서 MainActivity 쉼표 Gleo navigation hook과 Room 계측용 Gradle/schema export만 추가. 차박 navigation 분기는 변경하지 않음
- 기존 변경 초기화 없음
- 참고 NARU 프로젝트 수정 없음
- 커밋·push·merge 미수행
