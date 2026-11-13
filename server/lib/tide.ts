import { stamp } from "./time";
import { NIGHT_END, NIGHT_START, type NightWindow } from "./night";
import { fetchJson } from "./http";

/**
 * 국립해양조사원 조석예보(고, 저조) 조회 (공공데이터포털 데이터셋 15156018).
 * 요청: serviceKey, obsCode(예보지점 코드), reqDate(yyyyMMdd), type(json), numOfRows
 *
 * TODO(확인 필요): 공식 문서에 응답 필드 이름이 나와 있지 않다. 문서 설명(예측일시, 극치구분)을 근거로
 * 흔한 이름 후보를 함께 받는 너그러운 파서를 두고, 키 발급 후 실제 응답으로 이름을 확정한다.
 * 오퍼레이션 경로는 확인함 (2026-11-13): 잘못된 경로는 NO_OPENAPI_SERVICE_ERROR, 이 경로는 키 오류로 응답한다.
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
  const when: string | undefined = item?.predcDt ?? item?.tph_time ?? item?.record_time ?? item?.predcTm;
  const kind: string | undefined = String(item?.extrSe ?? item?.hl_code ?? item?.extrmSe ?? "");
  if (!when) return null;
  const digits = when.replace(/\D/g, "");
  if (digits.length < 12) return null;
  // 극치구분: 문자("고조"/"저조") 또는 코드(1·3 고조, 2·4 저조 — 일 2회 고조·저조)를 모두 받는다
  const high = kind.includes("고") || kind === "1" || kind === "3";
  return { date: digits.slice(0, 8), time: digits.slice(8, 12), high };
}

/** 오늘 밤 구간의 만조(고조) 시각 "HH:mm" 목록 */
export function nightHighTides(items: unknown[], night: NightWindow): string[] {
  const from = stamp(night.eveningDate, NIGHT_START);
  const to = stamp(night.morningDate, NIGHT_END);
  return items
    .map(readTideItem)
    .filter((t): t is NonNullable<typeof t> => t !== null && t.high)
    .filter((t) => {
      const s = stamp(t.date, t.time);
      return s >= from && s <= to;
    })
    .sort((a, b) => stamp(a.date, a.time) - stamp(b.date, b.time))
    .map((t) => `${t.time.slice(0, 2)}:${t.time.slice(2, 4)}`);
}
