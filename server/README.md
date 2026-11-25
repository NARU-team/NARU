# NARU 중계 서버

앱이 외부 API(기상청·국립해양조사원·LLM)를 직접 부르지 않도록 중간에서 받아 주는 서버다. 외부 서비스 키는 여기에만 둔다.
TypeScript, Vercel 서버리스 함수(Node.js 런타임).

## API

| 경로 | 내용 |
| --- | --- |
| `GET /api/sites` | 시연 지역 3곳 (숲·호수·해변) |
| `GET /api/sites/{id}/conditions?wake=HHmm` | 오늘 밤 최저기온·강수·강풍, 밤사이 만조(해변만), 일출, 기상 시각 무렵 날씨 |
| `GET /api/nearby?lat&lon` | 근처 야영장 (한국관광공사 고캠핑, 반경 20km). 인허가상 폐업·휴업 제외, 영업 중이면 `licensed: true` |

외부 API가 실패하면 그 항목만 `null`로 응답한다(추정해서 채우지 않는다). 일출은 외부 API 없이 좌표로 계산한다.

## 개발

```bash
cd server
npm install
npm test          # 단위 테스트
npm run typecheck
```

## 자동차야영장업 인허가 데이터 갱신

`data/auto-campgrounds.json`은 행정안전부 「문화_자동차야영장업」 CSV로 만든다 (이슈 #69). 자동 다운로드 주소가 막혀 있어(403) 손으로 받는다.

1. [공공데이터포털 15045088](https://www.data.go.kr/data/15045088/fileData.do)에서 CSV를 내려받는다 (원본 CSV는 커밋하지 않는다)
2. 변환한다

   ```bash
   cd server
   npx tsx scripts/build-auto-campgrounds.ts ~/Downloads/문화_자동차야영장업.csv
   ```

3. `npm test`로 확인하고(실제 데이터 테스트 포함) 커밋·재배포한다

- CSV는 CP949, 좌표는 EPSG:5174(보정 포함)다. 데이터 설명에는 "보정계수 안 들어간"이라고 돼 있지만 고캠핑 위경도와 비교하면 보정 포함이 맞다 (`scripts/autoCampgroundsCsv.ts` 주석 참고)
- 매칭 기준(300m 등)은 `lib/autoCampgrounds.ts`의 `LICENSE_MATCH`

## 환경 변수

`.env.example` 참고. 공공데이터포털 일반 인증키의 **디코딩** 값이며 Vercel 환경 변수에만 넣는다.

| 변수 | 서비스 |
| --- | --- |
| `DATA_GO_KR_SERVICE_KEY` | 기상청 단기예보 조회서비스 |
| `DATA_GO_KR_SERVICE_KEY_BEACH` | 국립해양조사원 조석예보(고, 저조). 없으면 위 키를 쓴다 |

## 앱 연결

배포 주소를 레포 루트 `local.properties`에 넣는다. 비어 있으면 앱은 내장 샘플 데이터를 쓴다.

```
naru.serverUrl=https://<배포 주소>
```
