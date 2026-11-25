import { readFileSync, writeFileSync, mkdirSync, statSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parseCsv, toAutoCampgrounds } from "./autoCampgroundsCsv.js";

/**
 * 인허가 CSV → server/data/auto-campgrounds.json (이슈 #69)
 *
 * 사용법 (server 폴더에서):
 *   npx tsx scripts/build-auto-campgrounds.ts ~/Downloads/문화_자동차야영장업.csv
 *
 * 원본 CSV는 커밋하지 않는다. 공공데이터포털에서 다시 받아 이 스크립트를 실행하면 갱신된다
 * (자동 다운로드 주소는 403이라 손으로 받는다).
 */
const input = process.argv[2];
if (!input) {
  console.error("CSV 경로가 필요합니다: npx tsx scripts/build-auto-campgrounds.ts <파일.csv>");
  process.exit(1);
}

// 공공데이터포털 CSV는 CP949. Node의 TextDecoder("euc-kr")는 WHATWG 규격상 windows-949(CP949)로 읽는다
const text = new TextDecoder("euc-kr").decode(readFileSync(input));
const campgrounds = toAutoCampgrounds(parseCsv(text));

const out = {
  source: "행정안전부 문화_자동차야영장업 (data.go.kr 15045088)",
  // 파일 내려받은 날짜를 기준일로 남긴다 (데이터는 2일 전 기준으로 매일 갱신)
  downloadedAt: statSync(input).mtime.toISOString().slice(0, 10),
  count: campgrounds.length,
  campgrounds,
};

const here = dirname(fileURLToPath(import.meta.url));
const target = resolve(here, "../data/auto-campgrounds.json");
mkdirSync(dirname(target), { recursive: true });
writeFileSync(target, JSON.stringify(out) + "\n");

const byStatus = campgrounds.reduce<Record<string, number>>((acc, c) => {
  acc[c.status] = (acc[c.status] ?? 0) + 1;
  return acc;
}, {});
const noPoint = campgrounds.filter((c) => c.lat === null).length;
console.log(`저장: ${target}`);
console.log(`전체 ${campgrounds.length}건`, byStatus, `좌표 없음 ${noPoint}건`);
