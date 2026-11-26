import { DEFAULT_MODEL } from "../../lib/narration.js";
import { interpretComma, validateCommaRequest, type CommaRequest } from "../../lib/commaIntent.js";

export async function POST(request: Request): Promise<Response> {
  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: "JSON 본문이 필요합니다" }, { status: 400 });
  }
  const invalid = validateCommaRequest(body);
  if (invalid) return Response.json({ error: invalid }, { status: 400 });
  const result = await interpretComma(body as CommaRequest, {
    apiKey: process.env.OPENAI_API_KEY,
    model: process.env.OPENAI_MODEL || DEFAULT_MODEL,
  });
  if (!result) return Response.json({ error: "쉼표 설정 제안을 만들지 못했습니다" }, { status: 502 });
  return Response.json(result, { headers: { "Cache-Control": "no-store" } });
}
