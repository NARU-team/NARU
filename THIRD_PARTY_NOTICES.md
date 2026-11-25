# 외부 자료 출처

앱에 포함된 외부 자료와 사용 조건입니다.

## 밤 테마 소리

| 파일 | 출처 | 라이선스 |
| --- | --- | --- |
| `app/src/main/res/raw/beach.mp3` | 출처 확인 필요 (2026-11-15 교체) | 확인 필요 |
| `app/src/main/res/raw/forest.mp3` | 출처 확인 필요 (2026-11-15 교체) | 확인 필요 |
| `app/src/main/res/raw/lake.mp3` | 출처 확인 필요 (2026-11-15 교체, 강물 소리) | 확인 필요 |

- 나루 앱의 밤 테마 기능 일부로 사용합니다.
- TODO(확인 필요): 2026-11-15에 팀에서 받은 소리로 교체했습니다. 출처와 라이선스를 확인해 이 표를 채워야 합니다.

## 글꼴

| 파일 | 출처 | 라이선스 |
| --- | --- | --- |
| `app/src/main/res/font/asta_sans.ttf` | [Asta Sans](https://github.com/42dot/Asta-Sans) (42dot) 가변 글꼴 | [SIL Open Font License 1.1](licenses/OFL-AstaSans.txt) |

- Copyright 2024 The Asta Sans Project Authors. 글꼴 파일을 단독으로 판매할 수 없고, 수정본은 원래 이름을 쓸 수 없습니다.

## 아이콘

| 파일 | 출처 | 라이선스 |
| --- | --- | --- |
| `app/src/main/res/drawable/ic_*.xml` | [Material Symbols](https://github.com/google/material-design-icons) (Outlined, SVG를 벡터 리소스로 변환) | [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) |

- 피그마 아이콘과 가장 비슷한 것을 골라 임시로 사용합니다. 피그마 SVG를 받으면 같은 이름 파일을 교체합니다.


## 중계 서버 내장 데이터

| 파일 | 출처 | 이용 조건 |
| --- | --- | --- |
| `server/data/auto-campgrounds.json` | 행정안전부 「문화_자동차야영장업」 인허가 데이터 ([공공데이터포털 15045088](https://www.data.go.kr/data/15045088/fileData.do)) | 이용허락범위 제한 없음, 무료 |

- 근처 야영장(한국관광공사 고캠핑) 결과에서 인허가상 폐업·휴업인 곳을 빼고, 영업 중인 곳에 "인허가 등록 자동차야영장"을 표시하는 데 씁니다 (이슈 #69).
- 원본 CSV에서 이름·영업상태·좌표·주소만 뽑아 위경도로 변환했습니다. 기준일은 JSON의 `downloadedAt`에 있습니다.
- 좌표 변환에 [proj4js](https://github.com/proj4js/proj4js) (MIT)를 변환 스크립트에서만 씁니다 (서버 배포물에는 포함되지 않음).
