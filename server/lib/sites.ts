/**
 * 시연 지역 3곳 (MVP: 전국 데이터 대신 시연 지역만 내장).
 * 좌표는 실제 위치라 날씨·물때·일출은 실제 값을 받는다. 차박 허용 여부와 시설은 시연용으로 정한 값이다.
 * 판정 3단계가 모두 시연되도록 숲(정보 확인), 호수(현장 확인 필요: 허용 여부 불명확), 해변(물때 확인)으로 둔다.
 */
export type PlaceType = "BEACH" | "FOREST" | "LAKE";
export type CampingPermission = "ALLOWED" | "UNKNOWN" | "PROHIBITED";

export interface Site {
  id: string;
  name: string;
  placeType: PlaceType;
  permission: CampingPermission;
  facilities: string[];
  lat: number;
  lon: number;
  /** 조석예보 지점 코드. 해변만 있다. */
  tideObsCode?: string;
}

export const SITES: Site[] = [
  {
    id: "demo-forest",
    name: "가평 숲 캠핑장 (시연)",
    placeType: "FOREST",
    permission: "ALLOWED",
    facilities: ["화장실", "개수대", "주차장"],
    lat: 37.5838,
    lon: 127.4806,
  },
  {
    id: "demo-lake",
    name: "춘천 의암호 주차장 (시연)",
    placeType: "LAKE",
    permission: "UNKNOWN",
    facilities: ["화장실"],
    lat: 37.8813,
    lon: 127.6996,
  },
  {
    id: "demo-beach",
    name: "태안 꽃지해수욕장 (시연)",
    placeType: "BEACH",
    permission: "ALLOWED",
    facilities: ["화장실", "샤워장"],
    lat: 36.4989,
    lon: 126.3354,
    // 안흥(36.67, 126.13): 꽃지와 같은 서해 외해 쪽, 북쪽 약 20km (2026-11-13 응답으로 확인)
    tideObsCode: "DT_0067",
  },
];

/** 앱에 내보내는 장소 정보 (좌표·지점 코드는 서버 내부용) */
export function publicSite({ id, name, placeType, permission, facilities }: Site) {
  return { id, name, placeType, permission, facilities };
}

export function findSite(id: string): Site | undefined {
  return SITES.find((s) => s.id === id);
}
