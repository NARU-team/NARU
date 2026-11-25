import { test } from "node:test";
import assert from "node:assert/strict";
import { siteConditions } from "../lib/conditions.js";
import { findSite } from "../lib/sites.js";

test("키가 없으면 날씨·물때는 null이고 일출만 계산한다", async () => {
  const beach = findSite("demo-beach")!;
  const result = await siteConditions(beach, {
    serviceKey: undefined,
    now: new Date("2026-11-14T11:00:00Z"),
    wake: "0700",
    log: () => {},
  });
  assert.equal(result.weather, null);
  assert.equal(result.highTides, null);
  assert.equal(result.morningWeather, null);
  assert.match(result.sunrise ?? "", /^07:\d\d$/);
});
