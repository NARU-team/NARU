import { distanceKm } from "./geo.js";
import { fetchJson } from "./http.js";
import type { CampingPermission, PlaceType } from "./sites.js";

/**
 * 한국관광공사 고캠핑 정보 조회서비스 — 위치기반 목록 (data.go.kr 15101933, 이슈 #46).
 * 요청: serviceKey, MobileOS, MobileApp, mapX(경도), mapY(위도), radius(m, 최대 20000), _type=json
 *
 * TODO(확인 필요): 공식 문서에 응답 필드 이름이 없다. 관광공사 오픈API의 흔한 이름(facltNm, induty, lctCl, sbrsCl,
 * toiletCo, swrmCo, wtrplCo, mapX, mapY, addr1, contentId)을 기준으로 너그럽게 읽고, 키 승인 후 실제 응답으로 확정한다.
 */
const ENDPOINT = "https://apis.data.go.kr/B551011/GoCamping/locationBasedList";

export const NEARBY_LIMITS = {
  radiusM: 20_000,
  maxPlaces: 6,
};

export interface NearbyPlace {
  id: string;
  name: string;
  /** 업종 그대로 (예: "자동차야영장, 일반야영장") */
  kind: string | null;
  /** 입지로 추정한 장소 유형. 알 수 없으면 null (앱이 테마 기본값을 씀) */
  placeType: PlaceType | null;
  /** 자동차야영장이면 ALLOWED, 아니면 차박 허용 여부 확인 필요 */
  permission: CampingPermission;
  facilities: string[];
  distanceKm: number;
  lat: number;
  lon: number;
  address: string | null;
}

export async function fetchNearby(serviceKey: string, lat: number, lon: number): Promise<NearbyPlace[]> {
  const params = new URLSearchParams({
    serviceKey,
    MobileOS: "ETC",
    MobileApp: "NARU",
    _type: "json",
    numOfRows: "50",
    pageNo: "1",
    mapX: String(lon),
    mapY: String(lat),
    radius: String(NEARBY_LIMITS.radiusM),
  });
  const json = (await fetchJson(`${ENDPOINT}?${params}`)) as any;
  const header = json?.response?.header ?? json?.header;
  if (header?.resultCode && !["0000", "00"].includes(String(header.resultCode))) {
    throw new Error(`고캠핑 응답 오류: ${header.resultCode} ${header.resultMsg ?? ""}`);
  }
  const body = json?.response?.body ?? json?.body;
  const raw = body?.items?.item ?? body?.items ?? [];
  const items = Array.isArray(raw) ? raw : raw && typeof raw === "object" ? [raw] : [];
  return toNearby(items, lat, lon);
}

/** 응답 항목 → 거리순 상위 장소. 좌표·이름이 없는 항목은 버린다 */
export function toNearby(items: any[], lat: number, lon: number): NearbyPlace[] {
  return items
    .map((it): NearbyPlace | null => {
      const name = str(it?.facltNm ?? it?.name);
      const pLat = Number(it?.mapY);
      const pLon = Number(it?.mapX);
      if (!name || !Number.isFinite(pLat) || !Number.isFinite(pLon)) return null;
      const kind = str(it?.induty);
      return {
        id: str(it?.contentId) ?? `${pLat},${pLon}`,
        name,
        kind,
        placeType: placeTypeOf(str(it?.lctCl)),
        permission: permissionOf(kind),
        facilities: facilitiesOf(it),
        distanceKm: Math.round(distanceKm(lat, lon, pLat, pLon) * 10) / 10,
        lat: pLat,
        lon: pLon,
        address: str(it?.addr1),
      };
    })
    .filter((p): p is NearbyPlace => p !== null)
    .sort((a, b) => a.distanceKm - b.distanceKm)
    .slice(0, NEARBY_LIMITS.maxPlaces);
}

/** 입지 구분(예: "산,숲", "해변", "호수,강") → 장소 유형. 여러 개면 바다 > 호수 > 숲 순서로 본다 */
export function placeTypeOf(lctCl: string | null): PlaceType | null {
  if (!lctCl) return null;
  if (/해변|바다|섬/.test(lctCl)) return "BEACH";
  if (/호수|강/.test(lctCl)) return "LAKE";
  if (/산|숲|계곡/.test(lctCl)) return "FOREST";
  return null;
}

/** 업종에 자동차야영장이 있어야 차박 허용으로 본다 (데이터가 없다고 허용으로 간주하지 않음) */
export function permissionOf(kind: string | null): CampingPermission {
  return kind?.includes("자동차야영장") ? "ALLOWED" : "UNKNOWN";
}

/** 화장실·샤워장·개수대 개수 + 부대시설 목록에서 시설 이름을 뽑는다 */
export function facilitiesOf(it: any): string[] {
  const out: string[] = [];
  if (Number(it?.toiletCo) > 0) out.push("화장실");
  if (Number(it?.swrmCo) > 0) out.push("샤워장");
  if (Number(it?.wtrplCo) > 0) out.push("개수대");
  const extra = str(it?.sbrsCl)?.split(",").map((s) => s.trim()).filter(Boolean) ?? [];
  for (const e of extra) if (!out.includes(e)) out.push(e);
  return out.slice(0, 6);
}

function str(v: unknown): string | null {
  if (v === undefined || v === null) return null;
  const s = String(v).trim();
  return s ? s : null;
}
