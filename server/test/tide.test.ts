import { test } from "node:test";
import assert from "node:assert/strict";
import { nightHighTides, readTideItem } from "../lib/tide";

const night = { eveningDate: "20261114", morningDate: "20261115" };

test("밤 구간의 고조만 시간순으로 고른다", () => {
  const items = [
    { predcDt: "2026-11-14 14:55", extrSe: "고조" }, // 구간 밖
    { predcDt: "2026-11-14 21:10", extrSe: "저조" },
    { predcDt: "2026-11-15 02:40", extrSe: "고조" },
    { predcDt: "2026-11-14 19:05", extrSe: "고조" },
    { predcDt: "2026-11-15 09:30", extrSe: "저조" },
  ];
  assert.deepEqual(nightHighTides(items, night), ["19:05", "02:40"]);
});

test("극치구분 코드(1·3 고조)와 다른 필드 이름도 읽는다", () => {
  assert.deepEqual(readTideItem({ tph_time: "2026-11-15 03:12:00", hl_code: "1" }), { date: "20261115", time: "0312", high: true });
  assert.deepEqual(readTideItem({ predcDt: "202611150915", extrSe: "2" }), { date: "20261115", time: "0915", high: false });
});

test("알아볼 수 없는 항목은 건너뛴다", () => {
  assert.equal(readTideItem({ foo: 1 }), null);
  assert.deepEqual(nightHighTides([{ foo: 1 }, null], night), []);
});
