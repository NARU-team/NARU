import { DEFAULT_MODEL } from "../lib/narration.js";
import { interpret, validateRequest, type IntentRequest } from "../lib/intent.js";

/**
 * POST /api/intent — { utterance, history, context } → 200 { reply, needsClarification, proposals } 또는 502
 * 제안은 앱에서 범위 검사와 사용자 승인을 거친 뒤에만 실행된다.
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

  const result = await interpret(body as IntentRequest, {
    apiKey: process.env.OPENAI_API_KEY,
    model: process.env.OPENAI_MODEL || DEFAULT_MODEL,
  });
  if (!result) return Response.json({ error: "요청을 이해하지 못했습니다" }, { status: 502 });
  return Response.json(result, { headers: { "Cache-Control": "no-store" } });
}
