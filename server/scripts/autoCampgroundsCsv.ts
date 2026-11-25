import proj4 from "proj4";

/**
 * 행정안전부 「문화_자동차야영장업」 인허가 CSV → 서버 내장 JSON (이슈 #69).
 * 데이터: https://www.data.go.kr/data/15045088/fileData.do
 *
 * 파일 I/O 없이 순수 함수만 둔다 (테스트용). 실제 읽기·쓰기는 build-auto-campgrounds.ts.
 */

/**
 * 좌표계: 데이터 설명은 "보정계수 안들어간 Bessel 중부원점TM(EPSG:5174)"이지만,
 * 고캠핑 위경도와 비교하면 보정(경도 원점 127°0'10.405")이 들어간 EPSG:5174가 맞다
 * (2026-11-25 확인: 추암 19m·망상 37m·별비담 22m 차이 / 무보정 EPSG:2097은 240~420m 어긋남).
 * towgs84는 EPSG:5174의 Bessel → WGS84 7변수 값.
 */
export const EPSG_5174 =
  "+proj=tmerc +lat_0=38 +lon_0=127.0028902777778 +k=1 +x_0=200000 +y_0=500000 +ellps=bessel +units=m +no_defs " +
  "+towgs84=-115.80,474.99,674.11,1.16,-2.31,-1.63,6.43";

/** 서버가 쓰는 인허가 상태. 영업 중이 아니면 근처 목록에서 뺀다 */
export type LicenseStatus = "OPEN" | "SUSPENDED" | "CLOSED";

export interface AutoCampground {
  /** 관리번호 (지자체 인허가 고유값) */
  id: string;
  name: string;
  status: LicenseStatus;
  /** 좌표가 없는 행(약 1할)은 null. 이런 곳은 주소로만 맞춘다 */
  lat: number | null;
  lon: number | null;
  /** 도로명주소, 없으면 지번주소 */
  address: string | null;
}

/**
 * RFC 4180 CSV 파서. 큰따옴표로 감싼 칸 안의 쉼표·줄바꿈과 "" 이스케이프를 처리한다.
 * (주소에 "동해대로 6270-14, 망상제2오토캠핑장"처럼 쉼표가 들어 있어 split(",")로는 깨진다)
 */
export function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cell = "";
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') {
        cell += '"';
        i++;
      } else if (c === '"') {
        quoted = false;
      } else {
        cell += c;
      }
    } else if (c === '"') {
      quoted = true;
    } else if (c === ",") {
      row.push(cell);
      cell = "";
    } else if (c === "\n" || c === "\r") {
      if (c === "\r" && text[i + 1] === "\n") i++;
      row.push(cell);
      rows.push(row);
      row = [];
      cell = "";
    } else {
      cell += c;
    }
  }
  if (cell !== "" || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }
  return rows.filter((r) => r.some((v) => v.trim() !== ""));
}

/** 상세영업상태명 → 서버 상태. 모르는 값은 보수적으로 CLOSED(목록에서 뺌) */
export function statusOf(detail: string): LicenseStatus {
  const s = detail.trim();
  if (s === "영업중") return "OPEN";
  if (s === "휴업") return "SUSPENDED";
  return "CLOSED";
}

/** EPSG:5174 TM(x, y) → WGS84 위경도. 값이 비었거나 숫자가 아니면 null */
export function tmToWgs84(x: string, y: string): { lat: number; lon: number } | null {
  const tx = Number(x.trim());
  const ty = Number(y.trim());
  if (!x.trim() || !y.trim() || !Number.isFinite(tx) || !Number.isFinite(ty)) return null;
  const [lon, lat] = proj4(EPSG_5174, "WGS84", [tx, ty]);
  // 소수 6자리 ≈ 0.1m. JSON 크기를 줄인다
  return { lat: round6(lat), lon: round6(lon) };
}

/** CSV 행 전체 → 인허가 목록. 헤더 이름으로 칸을 찾는다 (열 순서가 바뀌어도 동작) */
export function toAutoCampgrounds(rows: string[][]): AutoCampground[] {
  const [header, ...body] = rows;
  if (!header) return [];
  const col = (name: string) => {
    const i = header.findIndex((h) => h.trim() === name);
    if (i < 0) throw new Error(`CSV에 '${name}' 열이 없습니다`);
    return i;
  };
  const iId = col("관리번호");
  const iName = col("사업장명");
  const iStatus = col("상세영업상태명");
  const iX = col("좌표정보(X)");
  const iY = col("좌표정보(Y)");
  const iRoad = col("도로명주소");
  const iJibun = col("지번주소");

  return body
    .map((r): AutoCampground | null => {
      const name = (r[iName] ?? "").trim();
      if (!name) return null;
      const point = tmToWgs84(r[iX] ?? "", r[iY] ?? "");
      const address = (r[iRoad] ?? "").trim() || (r[iJibun] ?? "").trim() || null;
      return {
        id: (r[iId] ?? "").trim(),
        name,
        status: statusOf(r[iStatus] ?? ""),
        lat: point?.lat ?? null,
        lon: point?.lon ?? null,
        address,
      };
    })
    .filter((c): c is AutoCampground => c !== null);
}

function round6(v: number): number {
  return Math.round(v * 1e6) / 1e6;
}
