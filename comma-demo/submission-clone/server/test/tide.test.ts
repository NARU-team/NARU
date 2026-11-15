import { test } from "node:test";
import assert from "node:assert/strict";
import { nightHighTides, nightLowTides, readTideItem } from "../lib/tide.js";

const night = { eveningDate: "20261114", morningDate: "20261115" };

test("밤 구간의 고조만 시간순으로 고른다", () => {
  // 실제 응답 모양 (안흥, 2026-11-13 확인)
  const items = [
    { obsvtrNm: "안흥", predcDt: "2026-11-14 14:55", predcTdlvVl: 540, extrSe: "1" }, // 구간 밖
    { obsvtrNm: "안흥", predcDt: "2026-11-14 21:10", predcTdlvVl: 90, extrSe: "2" },
    { obsvtrNm: "안흥", predcDt: "2026-11-15 02:40", predcTdlvVl: 600, extrSe: "3" },
    { obsvtrNm: "안흥", predcDt: "2026-11-14 19:05", predcTdlvVl: 610, extrSe: "1" },
    { obsvtrNm: "안흥", predcDt: "2026-11-15 09:30", predcTdlvVl: 120, extrSe: "4" },
  ];
  assert.deepEqual(nightHighTides(items, night), ["19:05", "02:40"]);
  assert.deepEqual(nightLowTides(items, night), ["21:10"]); // 09:30은 구간(~09:00) 밖
});

test("극치구분 1·3은 고조, 2·4는 저조", () => {
  assert.deepEqual(readTideItem({ predcDt: "2026-11-13 18:08", extrSe: "3" }), { date: "20261113", time: "1808", high: true });
  assert.deepEqual(readTideItem({ predcDt: "2026-11-13 11:46", extrSe: "2" }), { date: "20261113", time: "1146", high: false });
});

test("알아볼 수 없는 항목은 건너뛴다", () => {
  assert.equal(readTideItem({ foo: 1 }), null);
  assert.deepEqual(nightHighTides([{ foo: 1 }, null], night), []);
});
