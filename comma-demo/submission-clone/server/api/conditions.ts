import { publicSite } from "../lib/sites.js";
import { siteConditions } from "../lib/conditions.js";
import { hereSite, inKorea, matchSite } from "../lib/geo.js";

/**
 * GET /api/conditions?lat=..&lon=..&wake=HHmm — 차량 위치로 오늘 밤 정보 (이슈 #44)
 * → { site: 시연 지역이면 장소 정보, 아니면 null, distanceKm, conditions }
 * 시연 지역이 아니면 날씨·일출은 좌표로, 물때는 조석 지점 근처일 때만 (허용 여부·시설은 앱이 "확인 필요"로 표시)
 */
export async function GET(request: Request): Promise<Response> {
  const url = new URL(request.url);
  const lat = Number(url.searchParams.get("lat"));
  const lon = Number(url.searchParams.get("lon"));
  if (!Number.isFinite(lat) || !Number.isFinite(lon) || !inKorea(lat, lon)) {
    return Response.json({ error: "대한민국 안의 lat, lon이 필요합니다" }, { status: 400 });
  }
  const wakeParam = url.searchParams.get("wake") ?? "";
  const wake = /^([01]\d|2[0-3])[0-5]\d$/.test(wakeParam) ? wakeParam : "0700";

  const matched = matchSite(lat, lon);
  const site = matched?.site ?? hereSite(lat, lon);
  const conditions = await siteConditions(site, {
    serviceKey: process.env.DATA_GO_KR_SERVICE_KEY,
    tideKey: process.env.DATA_GO_KR_SERVICE_KEY_BEACH,
    now: new Date(),
    wake,
  });
  return Response.json(
    {
      site: matched ? publicSite(matched.site) : null,
      distanceKm: matched ? Math.round(matched.km * 10) / 10 : null,
      conditions,
    },
    { headers: { "Cache-Control": "public, s-maxage=600" } },
  );
}
