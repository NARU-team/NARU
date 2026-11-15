import type { Site } from "./sites.js";
import { toGrid } from "./kmaGrid.js";
import { fetchForecast, summarizeMorning, summarizeNight, type MorningWeather, type NightWeather } from "./kma.js";
import { fetchTideItems, nightHighTides, nightLowTides } from "./tide.js";
import { sunriseKst } from "./sun.js";
import { nightWindow } from "./night.js";

/** 앱 SiteConditions와 같은 모양. 불러오지 못한 항목은 null (추정해서 채우지 않는다). */
export interface SiteConditions {
  weather: NightWeather | null;
  /** 해변이 아니면 확인하지 않으므로 null */
  highTides: string[] | null;
  lowTides: string[] | null;
  sunrise: string | null;
  morningWeather: MorningWeather | null;
}

export interface ConditionsDeps {
  /** 기상청 단기예보 키 */
  serviceKey: string | undefined;
  /** 조석예보 키. 없으면 [serviceKey]를 쓴다 (공공데이터포털은 보통 계정당 키 하나) */
  tideKey?: string | undefined;
  now: Date;
  /** 기상 시각 HHmm. 도착 시점에는 모르므로 기본 07:00 */
  wake: string;
  log?: (message: string) => void;
}

/** 진단용: 조석예보 원본 항목 (공개 데이터, 키는 응답에 없음). `?debug=tide`로 본다. */
export async function rawTide(site: Site, deps: ConditionsDeps): Promise<unknown> {
  const key = deps.tideKey || deps.serviceKey;
  if (!key || !site.tideObsCode) return { error: "키 또는 조석 지점 없음" };
  const night = nightWindow(deps.now);
  try {
    return { obsCode: site.tideObsCode, items: await fetchTideItems(key, site.tideObsCode, night.eveningDate) };
  } catch (e) {
    return { error: String(e) };
  }
}

export async function siteConditions(site: Site, deps: ConditionsDeps): Promise<SiteConditions> {
  const { serviceKey, now, wake, log = console.warn } = deps;
  const tideKey = deps.tideKey || serviceKey;
  const night = nightWindow(now);
  const sunrise = sunriseKst(night.morningDate, site.lat, site.lon);

  if (!serviceKey) {
    log("DATA_GO_KR_SERVICE_KEY가 없어 날씨·물때를 불러오지 않음");
    return { weather: null, highTides: null, lowTides: null, sunrise, morningWeather: null };
  }

  const [forecast, tides] = await Promise.allSettled([
    fetchForecast(serviceKey, toGrid(site.lat, site.lon), now),
    site.tideObsCode
      ? Promise.all([
          fetchTideItems(tideKey!, site.tideObsCode, night.eveningDate),
          fetchTideItems(tideKey!, site.tideObsCode, night.morningDate),
        ]).then((days) => days.flat())
      : Promise.resolve(null),
  ]);

  if (forecast.status === "rejected") log(`기상청 예보 실패 (${site.id}): ${forecast.reason}`);
  if (tides.status === "rejected") log(`조석예보 실패 (${site.id}): ${tides.reason}`);

  const items = forecast.status === "fulfilled" ? forecast.value : null;
  return {
    weather: items ? summarizeNight(items, night) : null,
    highTides: tides.status === "fulfilled" && tides.value ? nightHighTides(tides.value, night) : null,
    lowTides: tides.status === "fulfilled" && tides.value ? nightLowTides(tides.value, night) : null,
    sunrise,
    morningWeather: items ? summarizeMorning(items, night, wake) : null,
  };
}
