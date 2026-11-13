import { test } from "node:test";
import assert from "node:assert/strict";
import { sunriseKst } from "../lib/sun.js";

function minutes(hhmm: string | null): number {
  assert.ok(hhmm);
  const [h, m] = hhmm.split(":").map(Number);
  return h * 60 + m;
}

// 한국천문연구원 일출몰 시각 기준 서울(37.5665, 126.978). 계산 오차 3분 이내면 통과.
const SEOUL = { lat: 37.5665, lon: 126.978 };
const cases: [string, string][] = [
  ["20240621", "05:11"], // 하지
  ["20241221", "07:43"], // 동지
  ["20240320", "06:35"], // 춘분 무렵
];

for (const [date, expected] of cases) {
  test(`서울 ${date} 일출은 ${expected} 무렵`, () => {
    const actual = sunriseKst(date, SEOUL.lat, SEOUL.lon);
    assert.ok(Math.abs(minutes(actual) - minutes(expected)) <= 3, `${actual} vs ${expected}`);
  });
}

test("서쪽(태안)이 동쪽(강릉)보다 해가 늦게 뜬다", () => {
  const taean = minutes(sunriseKst("20261114", 36.4989, 126.3354));
  const gangneung = minutes(sunriseKst("20261114", 37.7519, 128.8761));
  assert.ok(taean > gangneung);
});
