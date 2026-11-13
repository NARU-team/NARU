# NARU 중계 서버

앱이 외부 API(기상청·국립해양조사원·LLM)를 직접 부르지 않도록 중간에서 받아 주는 서버다. 외부 서비스 키는 여기에만 둔다.
TypeScript, Vercel 서버리스 함수(Node.js 런타임).

## API

| 경로 | 내용 |
| --- | --- |
| `GET /api/sites` | 시연 지역 3곳 (숲·호수·해변) |
| `GET /api/sites/{id}/conditions?wake=HHmm` | 오늘 밤 최저기온·강수·강풍, 밤사이 만조(해변만), 일출, 기상 시각 무렵 날씨 |

외부 API가 실패하면 그 항목만 `null`로 응답한다(추정해서 채우지 않는다). 일출은 외부 API 없이 좌표로 계산한다.

## 개발

```bash
cd server
npm install
npm test          # 단위 테스트
npm run typecheck
```

## 환경 변수

`.env.example` 참고. `DATA_GO_KR_SERVICE_KEY`는 공공데이터포털 일반 인증키의 **디코딩** 값이며 Vercel 환경 변수에만 넣는다.
필요한 활용 신청: 기상청 단기예보 조회서비스, 국립해양조사원 조석예보(고, 저조).

## 앱 연결

배포 주소를 레포 루트 `local.properties`에 넣는다. 비어 있으면 앱은 내장 샘플 데이터를 쓴다.

```
naru.serverUrl=https://<배포 주소>
```
