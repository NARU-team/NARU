import OpenAI from "openai";

/**
 * 세 순간의 멘트 생성 (기획서 "AI 에이전트: 세 순간의 멘트, 실패 시 준비된 문장").
 *
 * 원칙 (AGENTS.md 생성형 AI와 규칙 기반의 분리):
 * - 판정·온도·배터리 같은 값은 앱의 규칙 엔진이 정해서 보낸다. LLM은 그 사실을 문장으로 다듬기만 한다.
 * - 출력은 화면 문장과 음성에만 쓰이고 차량 제어로 이어지지 않는다.
 * - 검증을 통과하지 못하거나 실패하면 null → 앱이 준비된 템플릿 문장을 쓴다.
 */
export type Moment = "ARRIVAL" | "BEDTIME" | "MORNING";
export const MOMENTS: Moment[] = ["ARRIVAL", "BEDTIME", "MORNING"];

/** 앱이 보내는 사실. 값은 문자열·숫자·불리언·문자열 목록만 받는다. */
export type Facts = Record<string, string | number | boolean | string[] | null>;

/** 기본 모델. 환경 변수 OPENAI_MODEL로 바꾼다. openai SDK 7.56 기준 최신 mini 모델. */
export const DEFAULT_MODEL = "gpt-5.4-mini";

/**
 * 출력 제한 (제안값, 이슈 #23·#25).
 * - 아침: 경고·날씨·배터리·실내 온도까지 사실이 4개 (실측: 3문장 제한에 걸려 템플릿으로 대체됨)
 * - 잠들기 전: 설정·예상 잔량·배터리 주의·인사까지 5문장 (실측: 주의를 넣으면 5문장). 주의를 빼는 것보다 길어지는 게 낫다
 */
export const NARRATION_LIMITS = {
  maxChars: 180,
  maxSentences: { ARRIVAL: 3, BEDTIME: 5, MORNING: 4 } satisfies Record<Moment, number>,
};

const MOMENT_GUIDE: Record<Moment, string> = {
  ARRIVAL: "차박 장소에 막 도착한 순간이다. 안내 등급과 그 이유를 먼저 짧게 전한다. 등급이 '주의·이동 권장'이면 이동을 권하는 말투를 유지한다.",
  BEDTIME:
    "잠들기 직전이다. 오늘 밤 설정(온도, 기상 시각, 테마)을 차분하게 확인해 준다. 아침 예상 잔량이 있으면 함께 알려 주고, '주의'가 있으면 빼지 말고 꼭 전한다. 편히 쉬라는 인사로 끝낸다.",
  MORNING: "막 일어난 아침이다. 경고가 있으면 가장 먼저 전하고, 그다음 바깥 날씨와 차량 상태를 밝게 알려 준다.",
};

export function instructions(moment: Moment): string {
  return [
    "너는 차박하는 운전자를 돕는 차량 음성 안내 '나루'다.",
    MOMENT_GUIDE[moment],
    `규칙: 한국어 존댓말, ${NARRATION_LIMITS.maxSentences[moment]}문장 이하, ${NARRATION_LIMITS.maxChars}자 이하.`,
    "주어진 사실만 말한다. 사실에 없는 숫자·시각·장소를 만들지 않는다. 등급이나 경고를 약하게 바꾸지 않는다.",
    "소리 내어 읽을 문장이므로 이모지, 마크다운, 괄호, 목록을 쓰지 않는다. 문장만 출력한다.",
  ].join("\n");
}

/** 입력 검증. 잘못된 요청이면 이유 문자열, 괜찮으면 null. */
export function validateRequest(body: unknown): string | null {
  const b = body as { moment?: unknown; facts?: unknown };
  if (!b || typeof b !== "object") return "JSON 본문이 필요합니다";
  if (!MOMENTS.includes(b.moment as Moment)) return "moment는 ARRIVAL, BEDTIME, MORNING 중 하나여야 합니다";
  if (!b.facts || typeof b.facts !== "object" || Array.isArray(b.facts)) return "facts 객체가 필요합니다";
  const size = JSON.stringify(b.facts).length;
  if (size > 2_000) return "facts가 너무 깁니다";
  return null;
}

/**
 * 출력 검증. 통과하면 다듬은 문장, 아니면 null.
 * - 길이·문장 수 제한, 마크다운·이모지 금지
 * - 문장 속 숫자는 모두 사실(facts)에 있어야 한다 (LLM이 값을 지어내지 않았는지 확인)
 */
export function validateOutput(text: string, facts: Facts, moment: Moment = "ARRIVAL"): string | null {
  const t = text.replace(/\s+/g, " ").trim();
  if (!t || t.length > NARRATION_LIMITS.maxChars) return null;
  if (/[*#`_\[\]()]|\p{Extended_Pictographic}/u.test(t)) return null;
  // 문장 끝 = 마침표·물음표·느낌표 뒤에 공백이 오거나 글이 끝남 ("19.1°C" 같은 소수점은 문장 끝이 아님)
  const sentences = t.split(/(?<=[.!?。])\s+/).filter((s) => s.trim());
  if (sentences.length > NARRATION_LIMITS.maxSentences[moment]) return null;

  const allowed = new Set(numbersIn(JSON.stringify(facts)));
  if (numbersIn(t).some((n) => !allowed.has(n))) return null;
  return t;
}

/** 문자열 속 숫자 (06:09 → 6, 9 / 13.5 → 13.5 / -2 → 2). 앞자리 0은 무시한다. */
export function numbersIn(s: string): string[] {
  return (s.match(/\d+(?:\.\d+)?/g) ?? []).map((n) => String(Number(n)));
}

export interface NarrationDeps {
  apiKey: string | undefined;
  model: string;
  log?: (message: string) => void;
}

/** 멘트를 만든다. 실패하면 null (이유는 로그). */
export async function narrate(moment: Moment, facts: Facts, deps: NarrationDeps): Promise<string | null> {
  const { apiKey, model, log = console.warn } = deps;
  if (!apiKey) {
    log("OPENAI_API_KEY가 없어 멘트를 만들지 않음");
    return null;
  }
  const client = new OpenAI({ apiKey, timeout: 8_000, maxRetries: 0 });
  try {
    const response = await client.responses.create({
      model,
      instructions: instructions(moment),
      input: `사실(JSON):\n${JSON.stringify(facts)}`,
      reasoning: { effort: "low" },
      max_output_tokens: 1_000,
    });
    const text = validateOutput(response.output_text ?? "", facts, moment);
    if (!text) log(`멘트 검증 실패 (${moment}): ${response.output_text}`);
    return text;
  } catch (e) {
    if (e instanceof OpenAI.APIError) log(`OpenAI 오류 (${moment}): ${e.status} ${e.message}`);
    else log(`멘트 생성 실패 (${moment}): ${e}`);
    return null;
  }
}
