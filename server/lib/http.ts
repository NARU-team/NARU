/** 외부 API 호출 시간 제한. Vercel 함수 제한(15초)보다 짧게 둔다. */
const TIMEOUT_MS = 7_000;

/**
 * JSON을 받아 온다. 실패(네트워크, 시간 초과, HTTP 오류, JSON이 아님)는 예외로 던지고,
 * 호출하는 쪽은 그 항목을 null로 처리한다.
 * 공공데이터포털은 인증 오류를 XML로 돌려주므로 본문 앞부분을 오류 메시지에 담는다.
 */
export async function fetchJson(url: string): Promise<unknown> {
  const res = await fetch(url, { signal: AbortSignal.timeout(TIMEOUT_MS) });
  const text = await res.text();
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${text.slice(0, 200)}`);
  try {
    return JSON.parse(text);
  } catch {
    throw new Error(`JSON이 아닌 응답: ${text.slice(0, 200)}`);
  }
}
