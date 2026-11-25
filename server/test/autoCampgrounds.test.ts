import { test } from "node:test";
import assert from "node:assert/strict";
import { districtOf, LICENSE_RECORDS, licenseOf, normalizeName, type LicenseRecord } from "../lib/autoCampgrounds.js";
import { toNearby } from "../lib/camping.js";

// 기준점: 추암오토캠핑장 (고캠핑 위경도)
const BASE = { lat: 37.47708927522, lon: 129.15920194843 };
/** 기준점에서 북쪽으로 m미터 (위도 1도 ≈ 111.195km, geo.ts 하버사인과 같은 지구 반지름) */
const north = (m: number) => BASE.lat + m / 111_195;

const rec = (over: Partial<LicenseRecord>): LicenseRecord => ({
  id: "X",
  name: "추암오토캠핑장",
  status: "OPEN",
  lat: BASE.lat,
  lon: BASE.lon,
  address: "강원특별자치도 동해시 촛대바위길 6",
  ...over,
});
const place = { name: "추암오토캠핑장", lat: BASE.lat, lon: BASE.lon, address: "강원특별자치도 동해시 촛대바위길 28" };

test("normalizeName: 공백·법인 표기·괄호를 무시한다", () => {
  assert.equal(normalizeName("아름다운 캠프"), normalizeName("아름다운캠프"));
  assert.equal(normalizeName("(주)하이원 추추파크"), normalizeName("하이원추추파크"));
});

test("districtOf: 두 번째 낱말", () => {
  assert.equal(districtOf("강원특별자치도 동해시 촛대바위길 6"), "동해시");
  assert.equal(districtOf(null), null);
});

test("거리 경계: 299m는 같은 곳, 301m는 다른 곳", () => {
  assert.equal(licenseOf(place, [rec({ lat: north(299) })]), "OPEN");
  assert.equal(licenseOf(place, [rec({ lat: north(301) })]), null);
});

test("이름: 한쪽이 포함하면 맞추고, 4글자 미만 일반 단어끼리는 맞추지 않는다", () => {
  assert.equal(licenseOf(place, [rec({ name: "동해 추암오토캠핑장" })]), "OPEN");
  assert.equal(licenseOf({ ...place, name: "캠핑장" }, [rec({ name: "추암 캠핑장" })]), null);
  assert.equal(licenseOf(place, [rec({ name: "망상오토캠핑리조트" })]), null);
});

test("좌표 없는 기록: 이름이 정확히 같고 시·군·구가 같을 때만", () => {
  const noPoint = { lat: null, lon: null };
  assert.equal(licenseOf(place, [rec({ ...noPoint })]), "OPEN");
  assert.equal(licenseOf(place, [rec({ ...noPoint, address: "강원특별자치도 삼척시 근덕면" })]), null);
  assert.equal(licenseOf(place, [rec({ ...noPoint, name: "동해 추암오토캠핑장" })]), null, "좌표 없으면 포함 관계로는 안 맞춤");
});

test("상태 합치기: 영업 중이 하나라도 있으면 영업 중 (운영자 변경으로 옛 기록이 폐업)", () => {
  assert.equal(licenseOf(place, [rec({ status: "CLOSED" }), rec({ status: "CLOSED" }), rec({ status: "OPEN" })]), "OPEN");
  assert.equal(licenseOf(place, [rec({ status: "CLOSED" }), rec({ status: "SUSPENDED" })]), "SUSPENDED");
  assert.equal(licenseOf(place, [rec({ status: "CLOSED" })]), "CLOSED");
  assert.equal(licenseOf(place, []), null);
});

test("toNearby: 폐업·휴업은 빼고 다음으로 가까운 곳이 자리를 채운다, 영업 중이면 licensed·ALLOWED", () => {
  const item = (id: string, name: string, dLat: number, induty = "일반야영장") => ({
    contentId: id, facltNm: name, mapY: String(BASE.lat + dLat), mapX: String(BASE.lon), induty,
  });
  const items = [
    item("1", "폐업캠핑장", 0.001),
    item("2", "휴업캠핑장", 0.002),
    item("3", "인허가캠핑장", 0.003),
    item("4", "모르는캠핑장", 0.004),
    ...[5, 6, 7, 8, 9].map((n) => item(String(n), `먼캠핑장${n}`, 0.01 * n)),
  ];
  const licenses = [
    rec({ name: "폐업캠핑장", status: "CLOSED", lat: BASE.lat + 0.001 }),
    rec({ name: "휴업캠핑장", status: "SUSPENDED", lat: BASE.lat + 0.002 }),
    rec({ name: "인허가캠핑장", status: "OPEN", lat: BASE.lat + 0.003 }),
  ];
  const places = toNearby(items, BASE.lat, BASE.lon, licenses);
  assert.deepEqual(places.map((p) => p.id), ["3", "4", "5", "6", "7", "8"], "6곳이 채워진다");
  assert.equal(places[0].licensed, true);
  assert.equal(places[0].permission, "ALLOWED", "고캠핑 업종이 일반야영장이어도 인허가 영업 중이면 허용");
  assert.equal(places[1].licensed, false);
  assert.equal(places[1].permission, "UNKNOWN");
});

test("실제 데이터: 아름다운캠프(고캠핑에 있지만 폐업)는 빠지고 추암오토캠핑장은 licensed", () => {
  const items = [
    { contentId: "a", facltNm: "아름다운캠프", mapY: "37.5747", mapX: "129.1134", induty: "일반야영장" },
    { contentId: "2975", facltNm: "추암오토캠핑장", mapY: String(BASE.lat), mapX: String(BASE.lon), induty: "자동차야영장" },
  ];
  const places = toNearby(items, BASE.lat, BASE.lon, LICENSE_RECORDS);
  assert.deepEqual(places.map((p) => [p.id, p.licensed]), [["2975", true]]);
});
