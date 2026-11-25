import { fetchNearby } from "../lib/camping.js";
import { inKorea } from "../lib/geo.js";

/**
 * GET /api/nearby?lat&lon — 차량 근처 야영장 (한국관광공사 고캠핑, 반경 20km, 이슈 #46)
 * → { places: [...], available }  키가 없거나 조회에 실패하면 places는 빈 목록, available=false
 */
export async function GET(request: Request): Promise<Response> {
  const url = new URL(request.url);
  const lat = Number(url.searchParams.get("lat"));
  const lon = Number(url.searchParams.get("lon"));
  if (!Number.isFinite(lat) || !Number.isFinite(lon) || !inKorea(lat, lon)) {
    return Response.json({ error: "대한민국 안의 lat, lon이 필요합니다" }, { status: 400 });
  }
  const key = process.env.DATA_GO_KR_SERVICE_KEY_CAMPING || process.env.DATA_GO_KR_SERVICE_KEY;
  if (!key) return Response.json({ places: [], available: false });
  try {
    const places = await fetchNearby(key, lat, lon);
    return Response.json({ places, available: true }, { headers: { "Cache-Control": "public, s-maxage=3600" } });
  } catch (e) {
    console.warn(`고캠핑 조회 실패: ${e}`);
    return Response.json({ places: [], available: false });
  }
}
