import { test } from "node:test";
import assert from "node:assert/strict";
import { facilitiesOf, permissionOf, placeTypeOf, toNearby } from "../lib/camping.js";

test("입지로 장소 유형을 정한다", () => {
  assert.equal(placeTypeOf("해변"), "BEACH");
  assert.equal(placeTypeOf("섬,해변"), "BEACH");
  assert.equal(placeTypeOf("호수,강"), "LAKE");
  assert.equal(placeTypeOf("산,숲"), "FOREST");
  assert.equal(placeTypeOf("계곡"), "FOREST");
  assert.equal(placeTypeOf("도심"), null);
  assert.equal(placeTypeOf(null), null);
});

test("자동차야영장이어야 차박 허용", () => {
  assert.equal(permissionOf("일반야영장,자동차야영장"), "ALLOWED");
  assert.equal(permissionOf("글램핑,카라반"), "UNKNOWN");
  assert.equal(permissionOf(null), "UNKNOWN");
});

test("시설: 개수와 부대시설 목록", () => {
  assert.deepEqual(facilitiesOf({ toiletCo: "4", swrmCo: "0", wtrplCo: 2, sbrsCl: "전기,온수, 산책로" }), ["화장실", "개수대", "전기", "온수", "산책로"]);
  assert.deepEqual(facilitiesOf({}), []);
});

test("거리순 정렬, 이름·좌표 없는 항목은 버린다", () => {
  const items = [
    { contentId: "1", facltNm: "먼 캠핑장", mapX: "127.6", mapY: "37.7", induty: "일반야영장", lctCl: "산" },
    { contentId: "2", facltNm: "가까운 오토캠핑장", mapX: "127.49", mapY: "37.585", induty: "자동차야영장", lctCl: "숲", addr1: "경기 가평군" },
    { contentId: "3", facltNm: "", mapX: "127.5", mapY: "37.6" },
    { contentId: "4", facltNm: "좌표 없음" },
  ];
  const places = toNearby(items, 37.5838, 127.4806);
  assert.deepEqual(places.map((p) => p.id), ["2", "1"]);
  assert.equal(places[0].permission, "ALLOWED");
  assert.equal(places[0].placeType, "FOREST");
  assert.equal(places[0].address, "경기 가평군");
  assert.ok(places[0].distanceKm < 1);
});
