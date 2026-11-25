import { test } from "node:test";
import assert from "node:assert/strict";
import { distanceKm, hereSite, inKorea, matchSite, nearTideStation } from "../lib/geo.js";

test("거리: 서울시청 ↔ 부산시청 약 325km", () => {
  const km = distanceKm(37.5665, 126.978, 35.1796, 129.0756);
  assert.ok(km > 315 && km < 335, String(km));
});

test("시연 지역 3km 안이면 그 장소", () => {
  assert.equal(matchSite(36.5, 126.336)?.site.id, "demo-beach"); // 꽃지 바로 옆
  assert.equal(matchSite(37.5665, 126.978), null); // 서울
});

test("조석 지점은 20km 안일 때만", () => {
  assert.equal(nearTideStation(36.58, 126.2)?.code, "DT_0067");
  assert.equal(nearTideStation(37.5665, 126.978), null);
});

test("현재 위치는 허용 여부 확인 필요, 근처 조석 지점만 붙인다", () => {
  const here = hereSite(36.6, 126.2);
  assert.equal(here.permission, "UNKNOWN");
  assert.deepEqual(here.facilities, []);
  assert.equal(here.tideObsCode, "DT_0067");
  assert.equal(hereSite(37.5665, 126.978).tideObsCode, undefined);
});

test("대한민국 범위 밖은 받지 않는다", () => {
  assert.equal(inKorea(37.5, 127), true);
  assert.equal(inKorea(40.7, -74), false);
});
