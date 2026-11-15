/**
 * 한국 시간(KST, UTC+9) 계산. 서버(Vercel)는 UTC로 돌기 때문에 시각은 모두 여기서 바꾼다.
 * KST에는 서머타임이 없어 고정 9시간 차이로 충분하다.
 */
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

/** KST 기준 날짜·시각 */
export interface KstDateTime {
  /** yyyyMMdd */
  date: string;
  /** HHmm */
  time: string;
}

/** UTC 시각 → KST 벽시계 값 */
export function toKst(instant: Date): KstDateTime {
  const kst = new Date(instant.getTime() + KST_OFFSET_MS);
  return { date: ymd(kst), time: pad(kst.getUTCHours()) + pad(kst.getUTCMinutes()) };
}

/** KST 날짜(yyyyMMdd)에 일수를 더한다 */
export function addDays(date: string, days: number): string {
  const d = parseYmd(date);
  d.setUTCDate(d.getUTCDate() + days);
  return ymd(d);
}

/** yyyyMMdd → 그 날짜 00:00(UTC 기준 Date, 달력 계산용) */
export function parseYmd(date: string): Date {
  return new Date(Date.UTC(Number(date.slice(0, 4)), Number(date.slice(4, 6)) - 1, Number(date.slice(6, 8))));
}

/** KST yyyyMMdd + HHmm 을 비교 가능한 정수(yyyyMMddHHmm)로 */
export function stamp(date: string, time: string): number {
  return Number(date + time);
}

/** HHmm → HH:mm */
export function colon(time: string): string {
  return `${time.slice(0, 2)}:${time.slice(2, 4)}`;
}

export function pad(n: number): string {
  return String(n).padStart(2, "0");
}

function ymd(d: Date): string {
  return `${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}`;
}
