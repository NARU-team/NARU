import { stamp } from "./time.js";
import { NIGHT_END, NIGHT_START, type NightWindow } from "./night.js";
import { fetchJson } from "./http.js";

/**
 * 국립해양조사원 조석예보(고, 저조) 조회 (공공데이터포털 데이터셋 15156018).
 * 요청: serviceKey, obsCode(예보지점 코드), reqDate(yyyyMMdd), type(json), numOfRows
 *
 * 응답 항목 (2026-11-13 실제 응답으로 확인, 공식 문서에는 필드 이름이 없음):
 *   obsvtrNm 지점명, lat/lot 위경도, predcDt "yyyy-MM-dd HH:mm" 예측일시, predcTdlvVl 예측조위(cm),
 *   extrSe 극치구분 "1"·"3" 고조, "2"·"4" 저조 (하루 두 번씩)
 */
const ENDPOINT = "https://apis.data.go.kr/1192136/tideFcstHghLw/GetTideFcstHghLwApiService";

export async function fetchTideItems(serviceKey: string, obsCode: string, reqDate: string): Promise<unknown[]> {
  const params = new URLSearchParams({ serviceKey, obsCode, reqDate, type: "json", pageNo: "1", numOfRows: "50" });
  const json = (await fetchJson(`${ENDPOINT}?${params}`)) as any;
  const header = json?.header ?? json?.response?.header;
  if (header?.resultCode && header.resultCode !== "00") {
    throw new Error(`조석예보 응답 오류: ${header.resultCode} ${header.resultMsg ?? ""}`);
  }
  const body = json?.body ?? json?.response?.body;
  const items = body?.items?.item ?? body?.items ?? [];
  return Array.isArray(items) ? items : [items];
}

/** 조석 항목 하나를 (KST 일시, 고조 여부)로 읽는다. 알아볼 수 없으면 null. */
export function readTideItem(item: any): { date: string; time: string; high: boolean } | null {
  const when = item?.predcDt;
  if (typeof when !== "string") return null;
  const digits = when.replace(/\D/g, "");
  if (digits.length < 12) return null;
  const kind = String(item?.extrSe ?? "");
  return { date: digits.slice(0, 8), time: digits.slice(8, 12), high: kind === "1" || kind === "3" };
}

/** 오늘 밤 구간의 만조(고조) 시각 "HH:mm" 목록 */
export function nightHighTides(items: unknown[], night: NightWindow): string[] {
  return nightTides(items, night, true);
}

/** 오늘 밤 구간의 간조(저조) 시각 "HH:mm" 목록 (도착 안내 카드 문구, 이슈 #35) */
export function nightLowTides(items: unknown[], night: NightWindow): string[] {
  return nightTides(items, night, false);
}

function nightTides(items: unknown[], night: NightWindow, high: boolean): string[] {
  const from = stamp(night.eveningDate, NIGHT_START);
  const to = stamp(night.morningDate, NIGHT_END);
  return items
    .map(readTideItem)
    .filter((t): t is NonNullable<typeof t> => t !== null && t.high === high)
    .filter((t) => {
      const s = stamp(t.date, t.time);
      return s >= from && s <= to;
    })
    .sort((a, b) => stamp(a.date, a.time) - stamp(b.date, b.time))
    .map((t) => `${t.time.slice(0, 2)}:${t.time.slice(2, 4)}`);
}
