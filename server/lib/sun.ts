import { parseYmd, pad } from "./time";

/**
 * 일출 시각 계산 (NOAA Solar Calculator의 근사식).
 * 외부 API 없이 좌표와 날짜만으로 계산한다. 오차는 보통 1~2분이다.
 * 설계 문서 미확정 항목 "일출 시각 출처"를 서버 계산으로 정함 (이슈 #21).
 *
 * @param date KST 날짜 yyyyMMdd
 * @returns KST "HH:mm". 극지방처럼 해가 뜨지 않으면 null
 */
export function sunriseKst(date: string, lat: number, lon: number): string | null {
  const day = parseYmd(date);
  const start = Date.UTC(day.getUTCFullYear(), 0, 0);
  const dayOfYear = Math.round((day.getTime() - start) / 86_400_000);

  // 일출 무렵(현지 오전 6시)의 태양 위치로 계산
  const gamma = ((2 * Math.PI) / 365) * (dayOfYear - 1 + (6 - 12) / 24);
  const eqTime =
    229.18 *
    (0.000075 +
      0.001868 * Math.cos(gamma) -
      0.032077 * Math.sin(gamma) -
      0.014615 * Math.cos(2 * gamma) -
      0.040849 * Math.sin(2 * gamma));
  const decl =
    0.006918 -
    0.399912 * Math.cos(gamma) +
    0.070257 * Math.sin(gamma) -
    0.006758 * Math.cos(2 * gamma) +
    0.000907 * Math.sin(2 * gamma) -
    0.002697 * Math.cos(3 * gamma) +
    0.00148 * Math.sin(3 * gamma);

  const latRad = (lat * Math.PI) / 180;
  const zenith = (90.833 * Math.PI) / 180; // 대기 굴절과 태양 반지름 보정
  const cosHa = Math.cos(zenith) / (Math.cos(latRad) * Math.cos(decl)) - Math.tan(latRad) * Math.tan(decl);
  if (cosHa < -1 || cosHa > 1) return null;
  const ha = (Math.acos(cosHa) * 180) / Math.PI;

  const utcMinutes = 720 - 4 * (lon + ha) - eqTime;
  const kstMinutes = Math.round(utcMinutes + 9 * 60);
  const m = ((kstMinutes % 1440) + 1440) % 1440;
  return `${pad(Math.floor(m / 60))}:${pad(m % 60)}`;
}
