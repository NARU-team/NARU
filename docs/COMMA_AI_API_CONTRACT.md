# 쉼표 모드 AI 제안 API 계약

## 구현 상태

기존 차박 `POST /api/intent`와 `server/lib/intent.ts`는 변경하지 않았다. 쉼표는 독립 `POST /api/comma/intent`와 `server/lib/commaIntent.ts`를 사용한다.

Android는 `BuildConfig.SERVER_URL`이 설정되면 서버를 먼저 호출한다. timeout, HTTP 오류, 잘못된 JSON, 빈 제안이면 기존 로컬 규칙으로 fallback한다. 처리 출처는 `LLM_SERVER`, `LOCAL_RULE`, `GLEO`, `UNAVAILABLE`로 구분하며 실제 Gleo 결과가 없으면 GLEO를 생성하지 않는다.

## 요청

```json
{
  "requestId": "comma-1800000000000-1",
  "utterance": "20분 동안 따뜻하게 빗소리 들으면서 쉴래",
  "context": {
    "stage": "READY",
    "sessionId": null,
    "selectedMinutes": 15,
    "remainingMinutes": null,
    "currentTempC": 23.0,
    "recommendedTempC": 24.0,
    "tempMinC": 16.0,
    "tempMaxC": 30.0,
    "climateSupported": true,
    "ambientLightSupported": true,
    "sound": "SILENT",
    "screenBrightnessPercent": 30,
    "recentPreferenceSummary": "최근 확인된 선호 온도 24.0도"
  }
}
```

전체 DB나 위치·사용자 식별 정보는 보내지 않는다. 이력은 가장 최근 REAL 피드백의 선호 온도 한 줄만 선택적으로 전달한다. 본문은 4KB, 발화는 120자, 이력 요약은 240자로 제한한다.

## 응답

```json
{
  "requestId": "comma-1800000000000-1",
  "reply": "20분, 1도 높게, 빗소리로 바꿀까요?",
  "needsClarification": false,
  "proposals": [
    {
      "type": "SET_DURATION",
      "minutes": 20,
      "celsius": null,
      "deltaC": null,
      "sound": null,
      "percent": null
    },
    {
      "type": "ADJUST_TEMPERATURE",
      "minutes": null,
      "celsius": null,
      "deltaC": 1,
      "sound": null,
      "percent": null
    },
    {
      "type": "SET_REST_SOUND",
      "minutes": null,
      "celsius": null,
      "deltaC": null,
      "sound": "RAIN",
      "percent": null
    }
  ]
}
```

Strict JSON schema를 사용하며 모든 필드는 존재하고 사용하지 않는 값은 null이다. 최대 4개 복합 제안을 허용한다.

## 지원 액션

| 액션 | 파라미터 | 허용 단계 | 검증 |
| --- | --- | --- | --- |
| SET_DURATION | minutes | READY | 1..180 |
| SET_TEMPERATURE | celsius | READY, RESTING | 실제 차량 capability 범위 |
| ADJUST_TEMPERATURE | deltaC | READY, RESTING | -5..5, 0 제외, capability 필요 |
| SET_REST_SOUND | sound | READY, RESTING | SILENT/RAIN/OCEAN/FOREST/LAKE |
| SET_SCREEN_BRIGHTNESS | percent | READY, RESTING | 5..100 |
| SET_AMBIENT_LIGHT | percent | READY, RESTING | 0..100, 차량 조명 capability 필요 |
| EXTEND_REST | minutes | RESTING, WAKING | 5 또는 10 |

## 앱 적용 정책

1. 요청 ID와 생성 당시 단계를 저장한다.
2. 늦은 응답이 도착했을 때 요청 순번이나 단계가 바뀌었으면 폐기한다.
3. 변경 전후와 처리 출처를 표시한다.
4. 사용자 승인 시 단계와 capability를 다시 검증한다.
5. 승인된 변경만 규칙 기반 코드로 적용한다.
6. 차량 제어는 기존 백업→set→재조회 정책을 유지한다.

서버와 LLM은 차량 SDK를 호출하지 않는다. 실제 적용 전에는 “바꿨어요” 같은 완료형 문구를 표시하지 않는다.

## 실패 처리

- 400: 요청 JSON 또는 컨텍스트 검증 실패
- 502: API 키 없음, OpenAI 오류, timeout, 구조화 출력 검증 실패
- Android network timeout: 10초
- 실패 시 LOCAL_RULE fallback. fallback도 해석하지 못하면 UNAVAILABLE 안내
- fallback을 LLM 또는 Gleo 결과로 표시하지 않음

## Gleo Interface

Pleos STT는 ON_DEVICE 음성을 텍스트로 바꾸는 입력 수단이고, Gleo Interface는 별도 LLM SDK가 아니라 Gleo가 등록된 Android action을 호출하는 Function Calling 계약이다.

구현한 `app/src/main/assets/schema.json`은 공식 제한인 최대 3개 action에 맞춰 다음처럼 묶는다.

- `NARU_COMMA_PROPOSE_SETTINGS`: 시간·절대/상대 온도·화면·자연음·차량 조명을 하나 이상의 설정 제안으로 변환
- `NARU_COMMA_EXTEND_REST`: 5분 또는 10분 연장 제안
- `NARU_COMMA_GET_STATUS`: 현재 단계와 남은 시간 조회

`GleoCommaContract`는 action parameter를 기존 `CommaProposedChange`로 변환한 뒤 단계와 capability를 검증하고 출처를 `GLEO`로 표시한다. 제안은 자동 실행하지 않는다.

공식 callback status는 `success`, `asking`, `fail`이다. 사용자 승인이 필요한 설정은 7초 action timeout 안에 적용 성공으로 응답할 수 없으므로 우선 `asking`으로 앱 승인 화면을 안내해야 한다. 차량 적용 완료 전 `success`를 반환하지 않는다. 비동기 승인 완료를 원 action과 상관관계 있게 재개하는 별도 공식 식별자 계약은 확인되지 않아 구현 완료로 표시하지 않는다.

Manifest에는 쉼표 전용 exported Activity와 세 action filter를 등록했다. Activity는 공식 `ACTION_PARAM` JSON을 검증하고 중복 request ID를 거부하며 설정·연장 요청에는 7초 안에 `asking` callback을 보낸다. 상태 조회는 활성 여부·단계·남은 시간과 충전 관측 상태만 `success`로 반환한다. 설정 요청은 process queue를 통해 기존 RestViewModel의 승인 제안으로 전달되고 주차·capability·차박 소유권을 다시 검사한다.

ADB 명시적 Intent로 Activity 수신과 앱 프로세스의 크래시 없음은 확인했지만 이는 실제 Gleo AI 호출이 아니다. 공식 문서에서 별도 signature permission이나 호출 package attestation 계약은 확인되지 않았고 Activity는 Gleo 호출을 위해 exported 상태다. 따라서 action allowlist, 4,096자 제한, 키·타입·값 범위, request ID 중복·만료 검증을 적용하며 외부 Intent만으로 차량을 제어하지 않는다. 상태 조회도 활성 여부·단계·남은 시간과 충전 관측 상태 외 개인정보를 반환하지 않는다. 공식 문서에는 `asking` 뒤 같은 action의 비동기 최종 callback 상관관계 ID가 정의되어 있지 않아 승인 완료를 임의의 후속 callback으로 보내지 않는다. schema 심사 전이므로 실제 Gleo Action/Callback은 UNVERIFIED다.

MainActivity는 상태 조회를 제외한 유효 Gleo 설정·연장 요청을 기존 쉼표 navigation과 RestViewModel에 연결한다. 요청은 allowlist/길이/타입/범위/ID 중복을 먼저 검사하고, 앱 내 유효 시간 2분을 넘기거나 차박 세션이 활성화되면 폐기한다. 공식 7초 action timeout은 Activity가 즉시 `asking` callback을 보내 충족하며 앱 내 사용자 승인 시간과 혼동하지 않는다. onboarding 중에는 안전 화면을 건너뛰지 않고 요청 만료까지만 대기한다. 승인 전 차량 제어는 없고 승인 시 단계·기어·capability·소유권을 다시 검증한다. 앱 내부 버튼이 Gleo를 호출하는 역방향 API는 공식 가이드에서 확인되지 않았으며 내부 입력은 기존 Pleos STT→LLM 서버/LOCAL_RULE 흐름을 유지한다.
