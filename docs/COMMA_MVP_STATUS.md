# 쉼표 모드 MVP 상태

기준일: 2026-11-26
브랜치: `feature/comma-mvp-integration`

상태 정의: IMPLEMENTED / PARTIAL / MISSING / MOCK_ONLY / SDK_BLOCKED / UNVERIFIED

## 최종 기획서 확인

`docs/NARU_최종기획서.pdf`는 `pdftotext`가 설치되지 않아 macOS PDFKit으로 실제 본문을 추출했다. PDF의 쉼표 모드 흐름과 P0/P1 표를 README와 별도로 확인했다.

| 기획서 요구사항 | 상태 | 구현/근거 |
| --- | --- | --- |
| 15·30·45분 및 직접 설정, 종료 시각 | IMPLEMENTED | `CommaSettingsScreen.kt`, `RestSessionManager.kt` |
| 화면 밝기·음향 프리셋 | IMPLEMENTED | Figma v.3 소리 선택 화면과 비/바다/숲/호숫가/무음, 미리듣기·적용 분리 (`CommaSoundScreen.kt`, `GeneratedNatureSoundPlayer.kt`) |
| 알람, 5·10분 연장, 즉시 종료 | PARTIAL | AlarmManager 예약·재예약·취소, exact 불가 시 inexact fallback, 기본음과 오디오 포커스 구현. 실제 기기 백그라운드 동작은 UNVERIFIED |
| 실제 차량 배터리·기어·온도 | IMPLEMENTED | `GatewayCommaVehicleRepository.kt`; Pleos 에뮬레이터 실측은 UNVERIFIED |
| 공조·조명 승인 적용 및 복구 | PARTIAL | 공조 OFF 시 전원 백업→ON→ON 재조회→온도 백업→set→재조회 순서로 보완. 실패 시 원래 OFF 복구와 앱 기능 계속 제공. Room v6 복구 결과 연결 완료, 수정 APK의 실제 공조 성공은 UNVERIFIED |
| 휴식 기록·배터리·공조·피드백 | IMPLEMENTED | `comma-rest.db` v6, 세션 ID 기준 피드백 1회 저장, 항목별 복구 결과와 충전 관측 품질 보존 |
| 개인화 공조 추천·직접 온도 | IMPLEMENTED | REAL 만족 기록 추천과 실제 capability 범위 직접 선택, 추천 복귀, 명시 승인 적용 |
| 충분한 실측 기반 에너지 예측 | PARTIAL | HIGH는 전 구간 비충전+포트 미연결 REAL 3건 이상, LIMITED는 충전 상태 API 미지원+전 구간 포트 미연결 REAL 3건 이상을 별도 계산한다. 두 품질을 섞지 않으며 LIMITED는 참고값으로 표시. 실차 데이터 예측은 UNVERIFIED |
| 점진적 화면 밝기 | IMPLEMENTED | 종료 전 30초 단조 시계 기반 |
| 점진적 차량 조명 | PARTIAL | WAKING 30초 동안 6단계로 제한, 중복값 방지·연장/종료/주행 취소 구현. 실제 차량 검증은 UNVERIFIED |
| AI 자연어 설정 변경 제안 | PARTIAL | 독립 `/api/comma/intent`, Android 서버 client, timeout/오류 시 LOCAL_RULE fallback, 복합 제안, 직접 온도·조명 액션, 승인 시 재검증 구현. 실제 배포 서버/OpenAI 호출과 Gleo는 미검증 |
| 주차 및 주행 전환 | IMPLEMENTED | AAOS 기어 Flow, 주행 종료·복구 |
| 차박/쉼표 동시 제어 방지 | IMPLEMENTED | `SessionOwnershipManager.kt`; 상대 모드를 종료하지 않고 시작만 거절 |
| 진행 세션 프로세스 복구 | PARTIAL | Room v4 활성 세션, 재시작/재부팅/시각 변경/만료 판정, 소유권·차량 백업 복구 구현. 실제 재부팅·SDK 실패 시나리오는 UNVERIFIED |
| 내비게이션 문맥 | PARTIAL | 참고 구현 검토 완료. NaviHelper 의존성과 P0/P1 우선순위 때문에 아직 미통합 |

## Figma v.3 UI 고도화

- 10개 노드 중 설정, 로컬 요청 결과, 적용 중, 소리 선택, 직접 설정, 제어 미지원, 휴식 중, 알람/연장 8개 화면은 실제 스크린샷으로 확인했다.
- 종료와 제어 실패 2개 화면은 Figma Starter 호출 한도 때문에 실제 스크린샷 재확인이 불가능하여 UNVERIFIED로 남겼다. 디자인을 추측해 확정하지 않았다.
- 차량 상태바, driving_view, 시스템 GNB는 앱이 중복 구현하지 않는다. Compose는 앱에 할당된 viewport에서 가변 폭 레이아웃을 유지한다.
- 서버가 연결되면 쉼표 전용 LLM endpoint를 먼저 사용하고 실패 시 로컬 규칙으로 fallback한다. 화면에 LLM_SERVER/LOCAL_RULE 출처를 표시하며 Gleo AI라고 표기하지 않는다.
- 상세 대응표와 기획 충돌은 `COMMA_FIGMA_MAPPING.md`, `COMMA_REQUIREMENTS_DIFF.md`에 기록했다.

## 피드백과 개인화

- FINISHED 화면에서 만족도 1~5와 온도 피드백을 입력한다.
- DAO의 조건부 UPDATE가 `feedbackSubmittedAt IS NULL`일 때만 저장하여 중복을 막는다.
- DEMO와 Mock은 입력 및 개인화에서 제외한다.
- 개인화와 에너지 예측은 `RecordSource.REAL`만 사용한다.
- 직접 선택 온도는 ViewModel에서 추천값과 별도로 관리하며, 추천 갱신이 덮어쓰지 않는다.
- 종료 기록 재시도는 INSERT IGNORE로 처리하여 이미 저장된 피드백을 보존한다.

## AI 구분

- Pleos STT: 기존 `PleosSpeechInput`, ON_DEVICE, ko-KR을 쉼표 입력 UI에 연결했다. 실제 마이크 품질은 UNVERIFIED다.
- 일반 LLM: 기존 차박 `/api/intent`는 변경하지 않고 독립 `POST /api/comma/intent`를 추가했다. 서버 79개 테스트와 typecheck는 성공했으나 배포/API 키를 사용한 실제 호출은 UNVERIFIED다.
- Gleo AI: schema.json, 쉼표 전용 Activity, 3개 action filter, asking/success/fail callback과 기존 승인 제안·MainActivity 쉼표 navigation을 연결했다. 만료·중복·차박 활성·입력 범위를 검증하고 승인 전 차량을 제어하지 않는다. ADB Action 수신과 앱 진입은 확인했으며 실제 Gleo AI 및 schema 심사는 UNVERIFIED다.
- 쉼표 로컬 fallback은 LLM이 아니며 출처를 LOCAL_RULE로 표시한다. 승인 없이 차량을 제어하지 않는다.

## 내비게이션 검토

참고 프로젝트의 NaviHelper 구현은 읽기 전용으로 현재 경로 여부, 목적지 표시명, 남은 시간·거리와 ETA만 조회한다. 목적지를 설정하거나 외부 Directions API를 사용하지 않는다. lifecycle 구독 해제, 5초 query timeout, 60초 freshness 처리가 재사용 가능하다. 다만 통합 프로젝트에 NaviHelper 의존성과 권한을 추가해야 하므로 필수 쉼표 기능 이후로 보류한다.
