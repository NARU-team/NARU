import { addDays, toKst } from "./time";

/**
 * 오늘 밤 구간 (KST). 도착 판정과 아침 브리핑에 쓰는 날씨·물때는 이 구간 안의 값만 본다.
 * - 저녁 시작 18:00 ~ 다음 날 아침 09:00
 * - 새벽(00:00~08:59)에 부르면 이미 지나고 있는 "어젯밤~오늘 아침" 구간을 쓴다.
 * 시각 기준은 제안값이다 (이슈 #21). 앱은 이 안에서 22:00~07:00 만조만 판정에 쓴다.
 */
export const NIGHT_START = "1800";
export const NIGHT_END = "0900";

export interface NightWindow {
  /** 밤이 시작하는 날짜 yyyyMMdd */
  eveningDate: string;
  /** 아침 날짜 yyyyMMdd */
  morningDate: string;
}

export function nightWindow(now: Date): NightWindow {
  const { date, time } = toKst(now);
  const eveningDate = time < NIGHT_END ? addDays(date, -1) : date;
  return { eveningDate, morningDate: addDays(eveningDate, 1) };
}
