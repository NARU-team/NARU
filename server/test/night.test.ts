import { test } from "node:test";
import assert from "node:assert/strict";
import { nightWindow } from "../lib/night";
import { latestBase } from "../lib/kma";

// 입력은 UTC. KST = UTC + 9시간
test("저녁 8시(KST)에는 오늘 밤~내일 아침", () => {
  assert.deepEqual(nightWindow(new Date("2026-11-14T11:00:00Z")), { eveningDate: "20261114", morningDate: "20261115" });
});

test("새벽 3시(KST)에는 어젯밤~오늘 아침", () => {
  assert.deepEqual(nightWindow(new Date("2026-11-14T18:00:00Z")), { eveningDate: "20261114", morningDate: "20261115" });
});

test("오전 10시(KST)에는 오늘 밤~내일 아침", () => {
  assert.deepEqual(nightWindow(new Date("2026-11-15T01:00:00Z")), { eveningDate: "20261115", morningDate: "20261116" });
});

test("발표 10분 뒤부터 그 발표를 쓴다", () => {
  // KST 17:09 → 14시 발표, 17:10 → 17시 발표
  assert.deepEqual(latestBase(new Date("2026-11-14T08:09:00Z")), { baseDate: "20261114", baseTime: "1400" });
  assert.deepEqual(latestBase(new Date("2026-11-14T08:10:00Z")), { baseDate: "20261114", baseTime: "1700" });
});

test("새벽 2시 10분 전에는 전날 23시 발표를 쓴다", () => {
  // KST 2026-11-15 01:30
  assert.deepEqual(latestBase(new Date("2026-11-14T16:30:00Z")), { baseDate: "20261114", baseTime: "2300" });
});
