import { DEFAULT_MODEL, narrate, validateRequest, type Facts, type Moment } from "../lib/narration.js";

/**
 * POST /api/narration — { moment, facts } → 200 { text } 또는 502 { error }
 * 실패해도 앱은 준비된 템플릿 문장을 쓰므로 세션은 계속된다.
 */
export async function POST(request: Request): Promise<Response> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: "JSON 본문이 필요합니다" }, { status: 400 });
  }
  const invalid = validateRequest(body);
  if (invalid) return Response.json({ error: invalid }, { status: 400 });

  const { moment, facts } = body as { moment: Moment; facts: Facts };
  const text = await narrate(moment, facts, {
    apiKey: process.env.OPENAI_API_KEY,
    model: process.env.OPENAI_MODEL || DEFAULT_MODEL,
  });
  if (!text) return Response.json({ error: "멘트를 만들지 못했습니다" }, { status: 502 });
  return Response.json({ text }, { headers: { "Cache-Control": "no-store" } });
}
