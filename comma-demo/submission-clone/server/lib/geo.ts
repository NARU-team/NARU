import { SITES, type Site } from "./sites.js";

/**
 * 차량 위치로 장소 찾기 (이슈 #44). 시연 지역 근처면 그 장소, 아니면 "현재 위치"로 좌표만 쓴다.
 */
export const GEO_LIMITS = {
  /** 이 거리 안이면 그 시연 지역에 있는 것으로 본다 */
  siteMatchKm: 3,
  /** 이 거리 안의 조석 예보지점만 쓴다 (먼 지점의 물때는 맞지 않음) */
  tideStationKm: 20,
};

/** 조석 예보지점 (지금은 꽃지 인근 안흥만, 응답으로 좌표 확인함) */
export const TIDE_STATIONS = [{ code: "DT_0067", name: "안흥", lat: 36.67463, lon: 126.12955 }];

/** 대한민국 대략 범위 (기상청 격자 밖 좌표는 받지 않는다) */
export function inKorea(lat: number, lon: number): boolean {
  return lat >= 33 && lat <= 39 && lon >= 124 && lon <= 132;
}

/** 두 좌표 사이 거리(km), 하버사인 공식 */
export function distanceKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371;
  const rad = (d: number) => (d * Math.PI) / 180;
  const dLat = rad(lat2 - lat1);
  const dLon = rad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

/** 가장 가까운 시연 지역 (siteMatchKm 안일 때만) */
export function matchSite(lat: number, lon: number, sites: Site[] = SITES): { site: Site; km: number } | null {
  const nearest = sites
    .map((site) => ({ site, km: distanceKm(lat, lon, site.lat, site.lon) }))
    .sort((a, b) => a.km - b.km)[0];
  return nearest && nearest.km <= GEO_LIMITS.siteMatchKm ? nearest : null;
}

/** 가까운 조석 예보지점 (tideStationKm 안일 때만) */
export function nearTideStation(lat: number, lon: number): (typeof TIDE_STATIONS)[number] | null {
  const nearest = TIDE_STATIONS.map((s) => ({ s, km: distanceKm(lat, lon, s.lat, s.lon) })).sort((a, b) => a.km - b.km)[0];
  return nearest && nearest.km <= GEO_LIMITS.tideStationKm ? nearest.s : null;
}

/**
 * 시연 지역이 아닌 "현재 위치". 허용 여부는 데이터가 없으니 확인 필요(UNKNOWN), 시설은 모름.
 * 장소 유형은 좌표로 알 수 없어 앱이 사용자에게 묻는다 (여기서는 임시 값).
 */
export function hereSite(lat: number, lon: number): Site {
  return {
    id: "here",
    name: "현재 위치",
    placeType: "FOREST",
    permission: "UNKNOWN",
    facilities: [],
    lat,
    lon,
    tideObsCode: nearTideStation(lat, lon)?.code,
  };
}
