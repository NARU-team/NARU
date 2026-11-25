import data from "../data/auto-campgrounds.json" with { type: "json" };
import { distanceKm } from "./geo.js";

/**
 * 행정안전부 「문화_자동차야영장업」 인허가 데이터로 고캠핑 장소를 확인한다 (이슈 #69).
 * 인허가 데이터는 확인용으로만 쓰고, 이것만으로 장소를 새로 보여주지 않는다.
 * JSON은 scripts/build-auto-campgrounds.ts가 만든다.
 */

export type LicenseStatus = "OPEN" | "SUSPENDED" | "CLOSED";

export interface LicenseRecord {
  id: string;
  name: string;
  status: LicenseStatus;
  lat: number | null;
  lon: number | null;
  address: string | null;
}

export const LICENSE_MATCH = {
  /** 이름이 같아도 이보다 멀면 다른 곳으로 본다 (고캠핑과 인허가 좌표 차이는 확인한 곳 기준 대부분 수십 m) */
  maxDistanceM: 300,
  /** 이름 포함 관계로 맞출 때 짧은 이름의 최소 글자 수 ("캠핑장" 같은 일반 단어끼리 맞지 않게) */
  minContainedNameLength: 4,
};

export const LICENSE_SOURCE = data.source;
export const LICENSE_RECORDS = data.campgrounds as LicenseRecord[];

/** 비교용 이름: 공백·법인 표기·괄호 제거, 소문자 */
export function normalizeName(name: string): string {
  return name
    .replace(/\(주\)|㈜|주식회사|\(유\)|유한회사/g, "")
    .replace(/[\s()·\-_.]/g, "")
    .toLowerCase();
}

/** 주소의 시·군·구 (두 번째 낱말, 예: "강원특별자치도 동해시 …" → "동해시") */
export function districtOf(address: string | null): string | null {
  return address?.trim().split(/\s+/)[1] ?? null;
}

interface PlaceLike {
  name: string;
  lat: number;
  lon: number;
  address: string | null;
}

/**
 * 고캠핑 장소 → 인허가 상태. 맞는 기록이 없으면 null.
 * - 좌표가 있는 기록: 이름이 같거나 한쪽이 다른 쪽을 포함하고, 300m 이내
 * - 좌표가 없는 기록: 이름이 정확히 같고 시·군·구가 같을 때만 (위치로 검증할 수 없어 더 엄격하게)
 * - 같은 곳에 기록이 여러 개면 하나라도 영업 중이면 영업 중 (운영자가 바뀌면 옛 기록이 폐업으로 남는다)
 */
export function licenseOf(place: PlaceLike, records: LicenseRecord[] = LICENSE_RECORDS): LicenseStatus | null {
  const name = normalizeName(place.name);
  const district = districtOf(place.address);
  const matched = records.filter((r) => {
    const other = normalizeName(r.name);
    if (r.lat !== null && r.lon !== null) {
      if (!namesMatch(name, other)) return false;
      return distanceKm(place.lat, place.lon, r.lat, r.lon) * 1000 <= LICENSE_MATCH.maxDistanceM;
    }
    return name === other && district !== null && district === districtOf(r.address);
  });
  if (matched.length === 0) return null;
  if (matched.some((r) => r.status === "OPEN")) return "OPEN";
  if (matched.some((r) => r.status === "SUSPENDED")) return "SUSPENDED";
  return "CLOSED";
}

function namesMatch(a: string, b: string): boolean {
  if (a === b) return true;
  const [short, long] = a.length <= b.length ? [a, b] : [b, a];
  return short.length >= LICENSE_MATCH.minContainedNameLength && long.includes(short);
}
