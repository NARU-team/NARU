import { test } from "node:test";
import assert from "node:assert/strict";
import { toGrid } from "../lib/kmaGrid.js";

// 기상청 활용가이드 격자 위경도 표의 알려진 값
test("서울시청은 격자 60,127", () => {
  assert.deepEqual(toGrid(37.5665, 126.978), { nx: 60, ny: 127 });
});

test("기준점(북위 38도, 동경 126도)은 격자 43,136", () => {
  assert.deepEqual(toGrid(38.0, 126.0), { nx: 43, ny: 136 });
});

test("제주시는 격자 53,38", () => {
  assert.deepEqual(toGrid(33.4996, 126.5312), { nx: 53, ny: 38 });
});
