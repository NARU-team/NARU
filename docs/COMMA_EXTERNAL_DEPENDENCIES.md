# 쉼표 모드 외부 의존성

기준일: 2026-11-26

| 항목 | 상태 | 실제 근거 | 필요한 후속 |
| --- | --- | --- | --- |
| Gleo AI | PARTIAL / UNVERIFIED | schema.json, exported 전용 Activity, 세 filter, 입력 allowlist/길이/타입/범위/중복/만료 검증, asking callback, MainActivity 쉼표 navigation 연결. ADB cold/warm Action 수신과 NARU 화면 진입 확인 | Pleos Playground schema 제출·심사와 실제 Gleo AI Action/Callback 검증 |
| Pleos STT | IMPLEMENTED / UNVERIFIED | SpeechToText 3.3.1, ON_DEVICE, ko-KR, 기존 PleosSpeechInput을 쉼표 UI에 연결 | 실제 마이크/권한 거절/자연음 포커스 실기 검증 |
| 쉼표 LLM 서버 | IMPLEMENTED / UNVERIFIED | 독립 /api/comma/intent, 기존 OpenAI Responses provider 재사용, 서버 79 tests/typecheck 성공 | 배포 SERVER_URL과 OPENAI_API_KEY로 실제 호출 |
| 외부 음악 앱 | MISSING / 기획 확인 | Figma 버튼은 확인했으나 대상 패키지/prototype/공식 launch Intent 미확정 | 대상 음악 앱과 AAOS 실행 정책 확정 |
| 충전 상태 | IMPLEMENTED / AVD_PARTIAL | CAR_ENERGY·CAR_ENERGY_PORTS 선언, 기존 CarPropertyManager 읽기 전용 주입, 5개 property 초기조회·구독과 v6 관측 연결. Pleos v2.1.0 AVD는 EV_CHARGE_PORT_CONNECTED만 지원하고 false 반환 | EV_CHARGE_STATE를 지원하는 실차/이미지에서 충전 시작·종료 구독 검증 |
| NaviHelper | PARTIAL | 참고 NARU에 읽기 전용 client/repository 존재. 현 앱 의존성 없음, Figma v.3 경로 UI 없음 | 공용 Gradle/Manifest 영향 승인 후 별도 통합 |
| Room Migration 계측 | VERIFIED | Pleos AVD에서 v1/v2/v3/v4/v5→v6 총 5개 실행·통과, v6 schema export 생성 | 이후 DB 버전부터 export schema를 누적 보존 |
| Pleos Emulator | PARTIAL | Automotive_Ultrawide가 Pleos Connect v2.1.0/API 34, APK 설치·실행 성공 | 차량 제어/알람/재부팅 전체 수동 시나리오 |

## 공식 근거

- Pleos Connect: https://document.pleos.ai/en/docs/connect/guide/connect
- Gleo 핵심 개념: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts
- App Schema: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/app-capability
- Gleo Action/Callback 예제: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/examples
- Callback Schema: https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/action_callback
- Gleo schema 전달: https://document.pleos.ai/en/docs/pleos-only/app-integration-guide/guide/deliver-schema
- AAOS VehiclePropertyIds: https://developer.android.com/reference/android/car/VehiclePropertyIds
- Pleos SDK 및 Emulator: https://developers.pleos.ai/playground/discover/resources
- 음악/오디오 앱 가이드: https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/music-audio-app-developement-guide
- 로컬 Vehicle SDK 2.3.2 sources.jar의 EvBattery.kt

## 공용 변경 범위

사용자가 승인한 범위에서만 MainActivity의 쉼표 Gleo navigation hook과 app Gradle의 AndroidX Test/Room testing·instrumentation runner·Room v6 schema export를 추가했다. NaviHelper 의존성과 공용 VehicleGateway 계약은 변경하지 않았다.

## 사용자 작업 체크리스트

1. Pleos Playground의 App Schema 제출 절차에서 빌드 APK에 포함된 `app/src/main/assets/schema.json`과 package `ai.naru.app`을 등록한다.
2. 세 action 이름이 Manifest filter와 일치하는지 확인하고 schema 심사 결과를 기다린다. 심사 전 ADB 검증을 실제 Gleo AI 성공으로 간주하지 않는다.
3. 승인된 Pleos 이미지/계정에서 설정 제안·연장·상태 조회를 각각 호출해 `asking/success/fail` callback을 확인한다.
4. 실제 LLM 서버는 서버 환경에 `OPENAI_API_KEY`를 설정하고 필요 시 `OPENAI_MODEL`을 지정한다. Android `local.properties`에는 `naru.serverUrl=http://10.0.2.2:<port>`(호스트 로컬 서버) 또는 배포 HTTPS URL만 설정한다. 키는 앱에 넣지 않는다.
5. 종료/제어 실패 Figma 개별 PNG를 제공하면 미검증 2개 화면을 최종 시각 비교할 수 있다.
