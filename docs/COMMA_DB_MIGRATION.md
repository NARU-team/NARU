# 쉼표 모드 DB Migration

데이터베이스: `comma-rest.db`
Room 클래스: `RestDatabase`
현재 버전: v6

Figma v.3 항목별 복구 결과의 v5를 보존하면서, 충전 여부가 확인된 기록만 에너지 예측에 사용하기 위해 v5→v6 Migration을 추가했다. 기존 기록, 피드백, 진행 세션 테이블은 삭제하거나 재작성하지 않는다.

## 스키마 이력

- v1: 종료 기록의 ID, 시작/종료 시각, 계획/실제 시간, 종료 사유.
- v1→v2: 배터리, 실내외 온도, 공조 목표, 화면·조명·자연음, 만족도, 복구 결과 nullable 컬럼 추가.
- v2→v3: `temperatureFeedback`, `feedbackSubmittedAt`, `source NOT NULL DEFAULT 'UNKNOWN'` 추가. 기존 행은 출처를 증명할 수 없어 UNKNOWN으로 유지한다.
- v3→v4: singleton 진행 세션 테이블 `active_rest_session` 추가. 기존 `rest_records`는 변경하지 않는다.
- v4→v5: `rest_records`에 `hvacPowerRestore`, `climateTemperatureRestore`, `ambientLightRestore`, `screenBrightnessRestore`, `natureSoundRestore`, `alarmRestore`를 `TEXT NOT NULL DEFAULT 'UNKNOWN'`으로 추가한다.
- v5→v6: 시작/종료 충전 상태, 휴식 중 포트 연결·충전 감지, 관측 완전성, 에너지 데이터 품질을 추가한다. 과거 행은 `UNKNOWN`, nullable, `chargingObservationComplete=0`으로 유지한다.

## v4 진행 세션

`active_rest_session`은 세션 ID/소유권/단계, wall 및 elapsed 기준 시각, 선택·계획·연장 시간, 승인 환경, 공조·앰비언트 백업, 자연음·밝기, 복구 상태, 출처, 알람/종료 기록 idempotency 플래그를 저장한다.

복구 판정은 저장 시점의 wall/elapsed 차이로 같은 부팅, 프로세스 재시작, 재부팅, 시스템 시각 변경, 만료를 구분한다. 재부팅 후 과거 elapsedRealtime deadline을 재사용하지 않고 wall 종료 시각에서 새 deadline을 계산한다. 안전하게 재개할 수 없으면 행과 차량 백업을 삭제하지 않고 `NEEDS_MANUAL_CHECK`로 유지한다.

## v5 항목별 복구 결과

각 종료 기록은 공조 전원, 공조 목표 온도, 차량 앰비언트 조명, 화면 밝기, 자연음, 알람의 결과를 별도로 저장한다. 값은 `SUCCESS`, `FAILURE`, `UNSUPPORTED`, `UNKNOWN`, `MANUAL_CHECK` 중 하나다.

차량 항목의 SUCCESS는 set 콜백만으로 기록하지 않는다. 가능한 경우 재조회 값이 백업값과 일치해야 한다. 재조회할 수 없으면 UNKNOWN 또는 MANUAL_CHECK로 남긴다. 복구가 확인되지 않은 차량 백업은 삭제하지 않는다.

## v6 충전 관측과 데이터 품질

`chargingStateAtStart`, `chargingStateAtEnd`는 `CHARGING/NOT_CHARGING/UNKNOWN`을 구분한다. `chargePortConnectedDuringRest`와 `chargingDetectedDuringRest`는 과거 데이터나 조회 실패를 거짓(false)으로 만들지 않도록 nullable이다. `chargingObservationComplete`가 false이면 시작·종료가 비충전처럼 보여도 고신뢰도 예측에 사용하지 않는다.

품질은 `VERIFIED_NOT_CHARGING`, `PORT_ONLY_VERIFIED`, `CHARGING_DETECTED`, `PORT_CONNECTED`, `OBSERVATION_INCOMPLETE`, `UNSUPPORTED`, `PERMISSION_DENIED`, `READ_FAILED`, `UNKNOWN`으로 구분한다. `PORT_ONLY_VERIFIED`는 충전 상태 API가 명시적으로 미지원이면서 전체 구간 포트 미연결 구독이 유지된 경우뿐이며 NOT_CHARGING으로 변환하지 않는다. 이 기록은 HIGH와 섞지 않고 3건 이상일 때 LIMITED 참고 예측에만 사용한다. 관측 공백·조회 실패·권한 부족은 계속 제외한다. Wh 값은 AVD 미지원이며 v6에 추측해서 저장하지 않았다.

## 피드백과 중복 방지

- `saveFeedbackOnce`는 `id = sessionId AND feedbackSubmittedAt IS NULL` 조건부 UPDATE다.
- 종료 기록 INSERT는 `REPLACE` 대신 `IGNORE`를 사용한다. 동일 세션 종료 저장을 재시도해도 기존 행과 이미 저장된 피드백을 삭제·재생성하지 않는다.
- 복구 결과는 항목별 UPDATE로만 갱신하므로 기존 만족도와 온도 피드백을 덮어쓰지 않는다.
- 활성 세션의 `alarmIssued`, `completionRecorded`는 0→1 조건부 UPDATE API를 제공한다.

## Migration 연결과 검증 상태

`RestApplicationServices`는 `MIGRATION_1_2`부터 `MIGRATION_5_6`까지 모두 명시하며 destructive migration을 사용하지 않는다.

2026-11-26 Pleos Automotive AVD(API 34)에서 v1/v2/v3/v4/v5→v6 계측 테스트 5개가 발견·실행되어 모두 통과했다. v1 fixture는 Git 기준 커밋의 실제 v1 엔티티 정의를 사용하고, v2~v5는 실제 Migration 객체를 순서대로 적용해 구성했다. 기록, 버전별 만족도·온도 피드백, 활성 세션과 원본 백업, v6 UNKNOWN 기본값, 중복 종료 기록 방지와 피드백 보존을 검증했다. v6 schema export는 `app/schemas/ai.naru.app.comma.RestDatabase/6.json`에 생성했다.
