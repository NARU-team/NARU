import { test } from "node:test";
import assert from "node:assert/strict";
import { parseCsv, statusOf, tmToWgs84, toAutoCampgrounds } from "../scripts/autoCampgroundsCsv.js";
import { distanceKm } from "../lib/geo.js";

test("parseCsv: 따옴표 안의 쉼표·줄바꿈과 \"\" 이스케이프", () => {
  const rows = parseCsv('a,b,c\r\n1,"동해대로 6270-14, 망상",x\n2,"줄\n바꿈","따옴표 ""안"""\n');
  assert.deepEqual(rows, [
    ["a", "b", "c"],
    ["1", "동해대로 6270-14, 망상", "x"],
    ["2", "줄\n바꿈", '따옴표 "안"'],
  ]);
});

test("parseCsv: 마지막 줄바꿈이 없어도, 빈 줄은 버린다", () => {
  assert.deepEqual(parseCsv("a,b\n\n1,2"), [["a", "b"], ["1", "2"]]);
});

test("statusOf: 영업중·휴업 외에는 모두 CLOSED (보수적으로 목록에서 뺌)", () => {
  assert.equal(statusOf("영업중"), "OPEN");
  assert.equal(statusOf(" 휴업 "), "SUSPENDED");
  for (const s of ["폐업", "등록취소", "직권말소", "전출", "", "처음 보는 값"]) {
    assert.equal(statusOf(s), "CLOSED", s);
  }
});

test("tmToWgs84: 추암오토캠핑장 TM 좌표가 고캠핑 위경도와 50m 안", () => {
  // CSV 원본 값 (뒤 공백 포함) / 고캠핑 contentId 2975 위경도
  const p = tmToWgs84("390911.988860814    ", "443837.716730372    ");
  assert.ok(p);
  const meters = distanceKm(p.lat, p.lon, 37.47708927522, 129.15920194843) * 1000;
  assert.ok(meters < 50, `${meters.toFixed(0)}m`);
});

test("tmToWgs84: 비었거나 숫자가 아니면 null", () => {
  assert.equal(tmToWgs84("", ""), null);
  assert.equal(tmToWgs84("   ", "443837"), null);
  assert.equal(tmToWgs84("abc", "443837"), null);
});

test("toAutoCampgrounds: 헤더 이름으로 열을 찾고, 좌표 없으면 null·도로명 없으면 지번", () => {
  const header = ["사업장명", "관리번호", "상세영업상태명", "좌표정보(X)", "좌표정보(Y)", "도로명주소", "지번주소"];
  const rows = [
    header,
    ["아름다운 캠프", "ID1", "폐업", "386633.384500976", "454587.268895783", "동해시 대진1길 47-2", ""],
    ["좌표없는곳", "ID2", "영업중", "", "", "", "삼척시 도계읍 1"],
    ["", "ID3", "영업중", "", "", "", ""],
  ];
  const out = toAutoCampgrounds(rows);
  assert.equal(out.length, 2, "이름 없는 행은 버린다");
  assert.equal(out[0].status, "CLOSED");
  assert.ok(out[0].lat !== null && out[0].lon !== null);
  assert.deepEqual(out[1], { id: "ID2", name: "좌표없는곳", status: "OPEN", lat: null, lon: null, address: "삼척시 도계읍 1" });
});

test("toAutoCampgrounds: 필요한 열이 없으면 바로 오류", () => {
  assert.throws(() => toAutoCampgrounds([["사업장명"], ["x"]]), /관리번호/);
});
