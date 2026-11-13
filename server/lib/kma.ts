import { addDays, stamp, toKst } from "./time";
import { NIGHT_END, NIGHT_START, type NightWindow } from "./night";
import type { Grid } from "./kmaGrid";
import { fetchJson } from "./http";

/**
 * 기상청 단기예보 조회서비스 (공공데이터포털, VilageFcstInfoService_2.0 / getVilageFcst).
 * 응답 항목: category(TMP 기온, TMN 최저기온, SKY 하늘, PTY 강수형태, PCP 1시간 강수량, WSD 풍속 등),
 * fcstDate(yyyyMMdd), fcstTime(HHmm), fcstValue(문자열)
 */
const ENDPOINT = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";

/** 판정 기준값 (제안값, 이슈 #21). 바꿀 때는 이곳만 고친다. */
export const KMA_THRESHOLDS = {
  /** 1시간 강수량이 이 값(mm) 이상이면 강한 비 */
  heavyRainMmPerHour: 10,
  /** 풍속이 이 값(m/s) 이상이면 강풍. 기상청 단기예보 풍속 설명의 "강"(9 m/s 이상) 기준 */
  strongWindMs: 9,
};

export interface ForecastItem {
  category: string;
  fcstDate: string;
  fcstTime: string;
  fcstValue: string;
}

export type Precipitation = "NONE" | "RAIN" | "HEAVY_RAIN";
export type Sky = "CLEAR" | "CLOUDY" | "OVERCAST" | "RAIN" | "SNOW";

export interface NightWeather {
  minTempC: number;
  precipitation: Precipitation;
  strongWind: boolean;
}

export interface MorningWeather {
  tempC: number;
  sky: Sky;
}

/** 단기예보 발표 시각(HHmm). 발표 후 약 10분 뒤부터 조회된다. */
const BASE_TIMES = ["0200", "0500", "0800", "1100", "1400", "1700", "2000", "2300"];
const AVAILABLE_AFTER_MIN = 10;

/** 지금 조회할 수 있는 가장 최근 발표 시각 */
export function latestBase(now: Date): { baseDate: string; baseTime: string } {
  const { date, time } = toKst(new Date(now.getTime() - AVAILABLE_AFTER_MIN * 60_000));
  const candidates = BASE_TIMES.filter((t) => t <= time);
  if (candidates.length === 0) return { baseDate: addDays(date, -1), baseTime: "2300" };
  return { baseDate: date, baseTime: candidates[candidates.length - 1] };
}

export async function fetchForecast(serviceKey: string, grid: Grid, now: Date): Promise<ForecastItem[]> {
  const { baseDate, baseTime } = latestBase(now);
  const params = new URLSearchParams({
    serviceKey,
    pageNo: "1",
    numOfRows: "1000",
    dataType: "JSON",
    base_date: baseDate,
    base_time: baseTime,
    nx: String(grid.nx),
    ny: String(grid.ny),
  });
  const json = (await fetchJson(`${ENDPOINT}?${params}`)) as any;
  const code = json?.response?.header?.resultCode;
  if (code !== "00") throw new Error(`기상청 응답 오류: ${code} ${json?.response?.header?.resultMsg ?? ""}`);
  const items = json?.response?.body?.items?.item;
  if (!Array.isArray(items)) throw new Error("기상청 응답에 예보 항목이 없음");
  return items as ForecastItem[];
}

/** 오늘 밤 구간의 최저기온·강수·강풍. 구간 안에 기온 예보가 없으면 null (추정하지 않는다). */
export function summarizeNight(items: ForecastItem[], night: NightWindow): NightWeather | null {
  const from = stamp(night.eveningDate, NIGHT_START);
  const to = stamp(night.morningDate, NIGHT_END);
  const inNight = items.filter((i) => {
    const s = stamp(i.fcstDate, i.fcstTime);
    return s >= from && s <= to;
  });

  const temps = [
    ...inNight.filter((i) => i.category === "TMP"),
    // 일 최저기온(TMN)은 아침 06시 예보로 들어온다
    ...items.filter((i) => i.category === "TMN" && i.fcstDate === night.morningDate),
  ]
    .map((i) => Number(i.fcstValue))
    .filter(Number.isFinite);
  if (temps.length === 0) return null;

  const rainy = inNight.some((i) => i.category === "PTY" && i.fcstValue !== "0");
  const heavy = inNight.some(
    (i) => i.category === "PCP" && parsePrecipitationMm(i.fcstValue) >= KMA_THRESHOLDS.heavyRainMmPerHour,
  );
  const strongWind = inNight.some(
    (i) => i.category === "WSD" && Number(i.fcstValue) >= KMA_THRESHOLDS.strongWindMs,
  );

  return {
    minTempC: Math.min(...temps),
    precipitation: heavy ? "HEAVY_RAIN" : rainy ? "RAIN" : "NONE",
    strongWind,
  };
}

/** 기상 시각 무렵(정시 기준)의 기온과 하늘 상태. 예보가 없으면 null. */
export function summarizeMorning(items: ForecastItem[], night: NightWindow, wakeHHmm: string): MorningWeather | null {
  const hour = wakeHHmm.slice(0, 2) + "00";
  const at = (category: string) =>
    items.find((i) => i.category === category && i.fcstDate === night.morningDate && i.fcstTime === hour)?.fcstValue;

  const temp = Number(at("TMP"));
  if (!Number.isFinite(temp) || at("TMP") === undefined) return null;
  return { tempC: temp, sky: skyOf(at("SKY"), at("PTY")) };
}

/**
 * 강수형태(PTY: 0 없음, 1 비, 2 비/눈, 3 눈, 4 소나기)가 있으면 우선하고,
 * 없으면 하늘상태(SKY: 1 맑음, 3 구름많음, 4 흐림)를 쓴다.
 */
function skyOf(sky: string | undefined, pty: string | undefined): Sky {
  if (pty === "3") return "SNOW";
  if (pty && pty !== "0") return "RAIN";
  if (sky === "1") return "CLEAR";
  if (sky === "3") return "CLOUDY";
  return "OVERCAST";
}

/**
 * 1시간 강수량(PCP) 문자열 → mm.
 * 예: "강수없음" → 0, "1mm 미만"·"1.0mm 미만" → 0.5, "3.0mm" → 3, "30.0~50.0mm" → 30, "50.0mm 이상" → 50
 */
export function parsePrecipitationMm(value: string): number {
  if (value.includes("없음")) return 0;
  const n = parseFloat(value);
  if (!Number.isFinite(n)) return 0;
  if (value.includes("미만")) return n / 2;
  return n;
}
