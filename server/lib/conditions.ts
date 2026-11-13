import type { Site } from "./sites.js";
import { toGrid } from "./kmaGrid.js";
import { fetchForecast, summarizeMorning, summarizeNight, type MorningWeather, type NightWeather } from "./kma.js";
import { fetchTideItems, nightHighTides } from "./tide.js";
import { sunriseKst } from "./sun.js";
import { nightWindow } from "./night.js";

/** 앱 SiteConditions와 같은 모양. 불러오지 못한 항목은 null (추정해서 채우지 않는다). */
export interface SiteConditions {
  weather: NightWeather | null;
  /** 해변이 아니면 확인하지 않으므로 null */
  highTides: string[] | null;
  sunrise: string | null;
  morningWeather: MorningWeather | null;
}

export interface ConditionsDeps {
  serviceKey: string | undefined;
  now: Date;
  /** 기상 시각 HHmm. 도착 시점에는 모르므로 기본 07:00 */
  wake: string;
  log?: (message: string) => void;
}

export async function siteConditions(site: Site, deps: ConditionsDeps): Promise<SiteConditions> {
  const { serviceKey, now, wake, log = console.warn } = deps;
  const night = nightWindow(now);
  const sunrise = sunriseKst(night.morningDate, site.lat, site.lon);

  if (!serviceKey) {
    log("DATA_GO_KR_SERVICE_KEY가 없어 날씨·물때를 불러오지 않음");
    return { weather: null, highTides: null, sunrise, morningWeather: null };
  }

  const [forecast, tides] = await Promise.allSettled([
    fetchForecast(serviceKey, toGrid(site.lat, site.lon), now),
    site.tideObsCode
      ? Promise.all([
          fetchTideItems(serviceKey, site.tideObsCode, night.eveningDate),
          fetchTideItems(serviceKey, site.tideObsCode, night.morningDate),
        ]).then((days) => days.flat())
      : Promise.resolve(null),
  ]);

  if (forecast.status === "rejected") log(`기상청 예보 실패 (${site.id}): ${forecast.reason}`);
  if (tides.status === "rejected") log(`조석예보 실패 (${site.id}): ${tides.reason}`);

  const items = forecast.status === "fulfilled" ? forecast.value : null;
  return {
    weather: items ? summarizeNight(items, night) : null,
    highTides: tides.status === "fulfilled" && tides.value ? nightHighTides(tides.value, night) : null,
    sunrise,
    morningWeather: items ? summarizeMorning(items, night, wake) : null,
  };
}
