# 쉼표 모드 Figma v.3 매핑

기준 파일: `1jnpCls9UfuoXzRB9pAKhY`
페이지: `2026.11.25_v.3` (`2:4`)
쉼표 Section: `10:117901`
비교 페이지: `2026.11.13_v.2` (`2:3`)

## 화면 영역

전체 프레임은 2560×1440이고 앱에 할당된 `naru_app`은 1700×1184다. 상태바, 왼쪽 `driving_view`, application 배경, 하단 차량 GNB는 Pleos/AAOS 시스템 영역이므로 앱에서 중복 구현하지 않는다. Compose는 실제 전달된 viewport를 기준으로 900dp 이상에서 2열, 작은 창에서 단일 열을 사용한다. Figma px를 dp로 그대로 복사하지 않는다.

## 화면별 매핑

| Figma 화면 | Node | Compose | 확인된 v.3 변경과 연결 | 상태 |
| --- | --- | --- | --- | --- |
| 쉼표 설정 | 10:92479 | `CommaSettingsScreen.kt` | 추천 패널, 근거 pill, 15/30/45/직접, 축소 timeline, 2×2 환경 설정 카드, 고정 우선 액션 | IMPLEMENTED |
| 요청 반영 뒤 | 10:93149 | `CommaSettingsScreen.kt`, 자연어 제안 dialog | 원문 요청과 변경 전후 표시. 실제 Gleo가 아니므로 로컬 처리임을 명시 | PARTIAL |
| 바꾸는 중 | 10:93226 | `CommaApplyingScreen.kt` | SDK 요청과 재조회 사이 전용 진행 화면, 중복 요청 차단 | IMPLEMENTED |
| 소리 고르기 | 10:93392 | `CommaSoundScreen.kt` | 빗소리/해변/숲/호수/무음 radio 목록, 미리 듣기, 적용/취소 | IMPLEMENTED |
| 직접 정하기 | 10:116325 | `CommaSettingsScreen.kt` | 직접 시간 및 개별 온도·화면·소리·조명 설정. 취소 전 차량 미적용 | IMPLEMENTED |
| 제어 안 되는 차 | 10:117910 | `CommaSettingsScreen.kt`, `CommaExceptionScreen.kt` | 공조/조명 미지원과 앱 타이머·화면·소리·알람 유지 | IMPLEMENTED |
| 휴식 중 | 10:92556 | `CommaRestingScreen.kt` | 원형 남은 시간, 실내/소리/SOC 카드, 5분 더, 그만 쉬기 | IMPLEMENTED |
| 알람과 연장 | 10:93290 | `CommaWakingScreen.kt` | 화면 밝아짐, 알람, 시작→종료 SOC, 5/10분 더, 다 쉬기 | IMPLEMENTED |
| 쉼표 종료 | 10:93334 | `CommaFinishedScreen.kt` | 실제 시간·SOC·피드백, 복구 확인 필요 개수와 펼침 상세 | PARTIAL_VISUAL |
| 바꾸지 못함 | 13:133405 | `CommaExceptionScreen.kt` | 미지원/실패 분리, 사용자용 오류, 제한 재시도, 온도 변경 없이 계속 | PARTIAL_VISUAL |

배터리 예상 pill은 HIGH와 LIMITED를 구분한다. 전 구간 충전 상태 비충전과 포트 미연결이 확인된 REAL 기록 3건 이상만 HIGH 범위를 표시한다. 충전 상태 API가 명시적으로 미지원이지만 전 구간 포트 미연결을 관측한 REAL 기록은 HIGH와 섞지 않고 3건 이상일 때만 `참고` 범위로 표시한다. 충전/포트 연결·관측 공백·조회 실패·권한 부족·표본 부족은 예측하지 않는다. 실제 Gleo Action만 GLEO 출처로 표시하고 LOCAL_RULE/LLM_SERVER를 Gleo로 표시하지 않는다.

## Figma 접근 및 시각 검증

Figma MCP에서 상위 Section은 희소 metadata로 반환되어 각 프레임을 개별 조회했다. 10:92479, 10:93149, 10:93226, 10:93392, 10:116325, 10:117910, 10:92556, 10:93290의 실제 스크린샷을 확인했다. 10:93334와 13:133405 요청 시 Starter 호출 한도에 도달해 상위 metadata와 사용자가 제공한 화면 설명까지만 확인했다. 따라서 두 화면의 픽셀 단위 시각 일치는 완료로 표시하지 않는다.

## 디자인 요소

- 흰색 카드, 연회색 앱 배경, 파란 선택/진행 강조, 짙은 회색 primary action.
- 큰 원형 타이머, 20dp 안팎 카드 모서리, 48dp 이상의 터치 영역.
- 쉼표 전용 `CommaDesignTheme`과 `CommaDesign` 토큰만 사용했다. 공통 NaruTheme은 변경하지 않았다.
- Figma 원본 아이콘 에셋은 호출 한도로 다운로드 URL과 라이선스를 끝까지 검증하지 못했다. 기존 Canvas fallback을 임의로 새 SVG로 바꾸지 않았으며, 원본 에셋 적용은 UNVERIFIED다.
- 화면 전체 이미지를 UI로 사용하지 않았다.

## Pleos 실제 렌더링

Pleos Connect v2.1.0/API 34 `Automotive_Ultrawide`에서 APK를 설치하고 홈→쉼표 연동 안내→설정 화면을 실제 확인했다. 시스템 상태바, driving view, GNB는 앱 밖에서 제공되며 앱은 우측 viewport에만 렌더링된다. 수동 화면에서 확인된 과도한 상단 상태, 좁은 제안 카드, 높은 환경 카드, 겹치는 FAB를 수정했다. 새 Compose Preview에서는 제안/시간 열과 2×2 환경 카드가 동시에 표시되고 FAB 겹침이 없다. 새 APK의 홈과 RESTING viewport를 Pleos GUI AVD에서 다시 확인했다. 종료/제어 실패 원본 PNG의 개별 픽셀 수치는 압축 파일 목록만 확인했으므로 픽셀 일치 완료로 처리하지 않는다.
