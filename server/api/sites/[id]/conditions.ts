import { findSite } from "../../../lib/sites.js";
import { siteConditions } from "../../../lib/conditions.js";

/**
 * GET /api/sites/{id}/conditions?wake=HHmm — 오늘 밤 날씨·물때·일출, 내일 아침 날씨
 * 외부 API가 실패해도 200으로 응답하고 그 항목만 null로 둔다 (앱이 보수적으로 판정).
 */
export async function GET(request: Request): Promise<Response> {
  const url = new URL(request.url);
  const id = url.pathname.split("/").at(-2) ?? "";
  const site = findSite(id);
  if (!site) return Response.json({ error: "장소를 찾을 수 없습니다" }, { status: 404 });

  const wakeParam = url.searchParams.get("wake") ?? "";
  const wake = /^([01]\d|2[0-3])[0-5]\d$/.test(wakeParam) ? wakeParam : "0700";

  const conditions = await siteConditions(site, {
    serviceKey: process.env.DATA_GO_KR_SERVICE_KEY,
    now: new Date(),
    wake,
  });
  // 단기예보는 3시간마다 갱신되므로 10분만 캐시한다
  return Response.json(conditions, { headers: { "Cache-Control": "public, s-maxage=600" } });
}
