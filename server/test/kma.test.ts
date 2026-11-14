import { test } from "node:test";
import assert from "node:assert/strict";
import { nightHourly, parsePrecipitationMm, summarizeMorning, summarizeNight, type ForecastItem } from "../lib/kma.js";

const night = { eveningDate: "20261114", morningDate: "20261115" };
const item = (category: string, fcstDate: string, fcstTime: string, fcstValue: string): ForecastItem => ({
  category,
  fcstDate,
  fcstTime,
  fcstValue,
});

const calmNight: ForecastItem[] = [
  item("TMP", "20261114", "1500", "1"), // 밤 구간 밖이라 무시
  item("TMP", "20261114", "2100", "8"),
  item("TMP", "20261115", "0300", "5"),
  item("TMN", "20261115", "0600", "4"),
  item("TMP", "20261115", "0700", "6"),
  item("SKY", "20261115", "0700", "1"),
  item("PTY", "20261115", "0700", "0"),
  item("PTY", "20261114", "2100", "0"),
  item("WSD", "20261114", "2100", "3.1"),
  item("PCP", "20261114", "2100", "강수없음"),
  item("TMP", "20261115", "1200", "-3"), // 구간 밖
];

test("밤 구간의 최저기온은 기온과 일 최저기온 중 가장 낮은 값", () => {
  assert.deepEqual(summarizeNight(calmNight, night), {
    minTempC: 4,
    precipitation: "NONE",
    strongWind: false,
    maxWindMs: 3.1,
    hourly: [
      { time: "21:00", tempC: 8, windMs: 3.1 },
      { time: "03:00", tempC: 5, windMs: null },
    ],
  });
});

test("밤에 비 예보가 있으면 비, 시간당 10mm 이상이면 강한 비", () => {
  const rain = [...calmNight, item("PTY", "20261115", "0100", "1"), item("PCP", "20261115", "0100", "2.0mm")];
  assert.equal(summarizeNight(rain, night)?.precipitation, "RAIN");
  const heavy = [...rain, item("PCP", "20261115", "0200", "30.0~50.0mm")];
  assert.equal(summarizeNight(heavy, night)?.precipitation, "HEAVY_RAIN");
});

test("풍속 9m/s 이상이면 강풍, 최대 풍속을 함께 준다", () => {
  const windy = summarizeNight([...calmNight, item("WSD", "20261115", "0000", "9.4")], night);
  assert.equal(windy?.strongWind, true);
  assert.equal(windy?.maxWindMs, 9.4);
});

test("풍속 예보가 없으면 최대 풍속은 모른다", () => {
  assert.equal(summarizeNight(calmNight.filter((i) => i.category !== "WSD"), night)?.maxWindMs, null);
});

test("밤 구간에 기온 예보가 없으면 날씨를 모른다", () => {
  assert.equal(summarizeNight([item("TMP", "20261114", "1200", "10")], night), null);
});

test("기상 시각 정시의 기온과 하늘 상태, 낮 최고·바람", () => {
  assert.deepEqual(summarizeMorning(calmNight, night, "0730"), { tempC: 6, sky: "CLEAR", dayMaxTempC: 6, windMs: null });
  const withTmx = [...calmNight, item("TMX", "20261115", "1500", "13.0"), item("WSD", "20261115", "0700", "2.1")];
  assert.deepEqual(summarizeMorning(withTmx, night, "0700"), { tempC: 6, sky: "CLEAR", dayMaxTempC: 13, windMs: 2.1 });
});

test("강수형태가 있으면 하늘 상태보다 우선한다", () => {
  const snowy = [...calmNight.filter((i) => !(i.category === "PTY" && i.fcstTime === "0700")), item("PTY", "20261115", "0700", "3")];
  assert.equal(summarizeMorning(snowy, night, "0700")?.sky, "SNOW");
});

test("기상 시각 예보가 없으면 아침 날씨를 모른다", () => {
  assert.equal(summarizeMorning(calmNight, night, "0900"), null);
});

test("1시간 강수량 문자열을 mm로 바꾼다", () => {
  assert.equal(parsePrecipitationMm("강수없음"), 0);
  assert.equal(parsePrecipitationMm("1mm 미만"), 0.5);
  assert.equal(parsePrecipitationMm("3.0mm"), 3);
  assert.equal(parsePrecipitationMm("30.0~50.0mm"), 30);
  assert.equal(parsePrecipitationMm("50.0mm 이상"), 50);
});

test("밤 시각별 예보: 18시는 저녁 날짜, 00시 이후는 아침 날짜에서 찾는다", () => {
  const items = [
    item("TMP", "20261114", "1800", "9"),
    item("WSD", "20261114", "1800", "2"),
    item("TMP", "20261114", "0000", "1"), // 저녁 날짜의 00시는 지난 밤이라 무시
    item("TMP", "20261115", "0000", "6"),
    item("TMP", "20261115", "0600", "7"),
  ];
  assert.deepEqual(nightHourly(items, night), [
    { time: "18:00", tempC: 9, windMs: 2 },
    { time: "00:00", tempC: 6, windMs: null },
    { time: "06:00", tempC: 7, windMs: null },
  ]);
});
