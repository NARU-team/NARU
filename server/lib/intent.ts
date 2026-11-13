import OpenAI from "openai";
import { DEFAULT_MODEL } from "./narration.js";

/**
 * 발화 해석 (기획서 P1 "사용자 발화 해석과 설정 변경 제안").
 *
 * 원칙 (AGENTS.md): LLM은 "설정 변경 제안"까지만 만든다.
 *   형식 검증(여기 + 앱) → 사용자 승인(앱) → 규칙 기반 실행(앱) 순서를 거치고, LLM 출력이 차량 제어로 바로 이어지지 않는다.
 * 멀티턴: 앱이 최근 대화를 보내고, 서버는 대화를 저장하지 않는다.
 */
export const STAGES = ["BEDTIME_PREP", "NIGHT"] as const;
export type Stage = (typeof STAGES)[number];

export const ACTION_TYPES = [
  "SET_TEMPERATURE",
  "ADJUST_TEMPERATURE",
  "SET_WAKE_TIME",
  "WAKE_AT_SUNRISE",
  "SET_THEME",
  "SET_SOUND",
  "SET_VOICE",
] as const;
export type ActionType = (typeof ACTION_TYPES)[number];

/** 단계마다 제안할 수 있는 동작. 앱도 같은 규칙으로 한 번 더 거른다. */
export const ALLOWED: Record<Stage, ActionType[]> = {
  BEDTIME_PREP: ["SET_TEMPERATURE", "ADJUST_TEMPERATURE", "SET_WAKE_TIME", "WAKE_AT_SUNRISE", "SET_THEME"],
  NIGHT: ["SET_TEMPERATURE", "ADJUST_TEMPERATURE", "SET_SOUND", "SET_VOICE"],
};

export const THEMES = ["BEACH", "FOREST", "LAKE"] as const;

/** LLM이 제안했지만 모두 걸러졌을 때 답변 대신 쓰는 문장 (제안 카드 없이 "바꿀까요?"라고 묻지 않게) */
export const CANNOT_CHANGE_REPLY = "지금 단계에서는 그 설정을 바꿀 수 없어요.";

export const INTENT_LIMITS = {
  maxUtteranceChars: 100,
  maxHistoryTurns: 6,
  maxProposals: 3,
  maxReplyChars: 120,
  /** 온도 제안 형식 검사 범위. 실제 차량 범위는 앱이 다시 확인한다 */
  tempMinC: 10,
  tempMaxC: 35,
  maxDeltaC: 5,
};

export interface Proposal {
  type: ActionType;
  celsius: number | null;
  deltaC: number | null;
  time: string | null;
  theme: (typeof THEMES)[number] | null;
  on: boolean | null;
}

export interface IntentResult {
  reply: string;
  needsClarification: boolean;
  proposals: Proposal[];
}

export interface Turn {
  role: "user" | "assistant";
  text: string;
}

export interface IntentContext {
  stage: Stage;
  targetTempC: number | null;
  tempMinC: number | null;
  tempMaxC: number | null;
  wakeAt: string | null;
  sunrise: string | null;
  theme: string | null;
  soundOn: boolean | null;
  voiceOn: boolean | null;
}

export interface IntentRequest {
  utterance: string;
  history: Turn[];
  context: IntentContext;
}

/** OpenAI 구조화 출력 스키마 (strict: 모든 필드 필수, 없는 값은 null) */
export const INTENT_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["reply", "needsClarification", "proposals"],
  properties: {
    reply: { type: "string", description: "사용자에게 보여줄 한국어 존댓말 한두 문장" },
    needsClarification: { type: "boolean", description: "값을 정할 수 없어 되물을 때 true (이때 proposals는 비움)" },
    proposals: {
      type: "array",
      maxItems: INTENT_LIMITS.maxProposals,
      items: {
        type: "object",
        additionalProperties: false,
        required: ["type", "celsius", "deltaC", "time", "theme", "on"],
        properties: {
          type: { type: "string", enum: [...ACTION_TYPES] },
          celsius: { type: ["number", "null"], description: "SET_TEMPERATURE의 목표 온도" },
          deltaC: { type: ["number", "null"], description: "ADJUST_TEMPERATURE의 변화량 (따뜻하게 +, 시원하게 -)" },
          time: { type: ["string", "null"], description: "SET_WAKE_TIME의 24시간 HH:mm" },
          theme: { type: ["string", "null"], enum: [...THEMES, null] },
          on: { type: ["boolean", "null"], description: "SET_SOUND·SET_VOICE의 켜기(true)/끄기(false)" },
        },
      },
    },
  },
} as const;

const ACTION_GUIDE: Record<ActionType, string> = {
  SET_TEMPERATURE: "설정 온도를 특정 값으로 (celsius)",
  ADJUST_TEMPERATURE: "설정 온도를 올리거나 내림 (deltaC, '조금'은 1도)",
  SET_WAKE_TIME: "기상 시각 지정 (time, 24시간 HH:mm)",
  WAKE_AT_SUNRISE: "일출 시각에 깨우기",
  SET_THEME: "밤 테마 (theme: BEACH 해변, FOREST 숲, LAKE 호수)",
  SET_SOUND: "테마 소리 켜기/끄기 (on)",
  SET_VOICE: "음성 안내 켜기/끄기 (on)",
};

export function instructions(context: IntentContext): string {
  const allowed = ALLOWED[context.stage].map((t) => `- ${t}: ${ACTION_GUIDE[t]}`).join("\n");
  return [
    "너는 차박하는 운전자를 돕는 차량 에이전트 '나루'다. 사용자의 말을 설정 변경 '제안'으로 바꾼다.",
    "제안은 사용자가 승인해야 적용된다. 이미 바꿨다고 말하지 말고 '~로 바꿀까요?'처럼 제안한다.",
    `지금 단계에서 제안할 수 있는 동작:\n${allowed}`,
    "목록에 없는 동작은 이 단계에서 바꿀 수 없다고 답한다 (예: 밤 단계에서는 기상 시각·테마를 바꿀 수 없다).",
    "목록에 없는 요청(차량 이동, 문 열기, 기상 시각 외의 알람 등)은 제안하지 말고 할 수 없다고 짧게 답한다.",
    "값을 정할 수 없으면 needsClarification=true, proposals는 비우고 reply로 되묻는다 (예: '몇 시에 깨워 드릴까요?').",
    "이전 대화를 참고해 '아니 1도 더', '그거 말고 숲으로' 같은 고쳐 말하기를 해석한다. 이전 제안을 고칠 때는 지금 설정 기준의 최종값을 제안한다.",
    "지금 설정과 같은 값은 제안하지 않는다. reply는 한국어 존댓말 한두 문장, 이모지·마크다운 금지.",
    `지금 설정(JSON): ${JSON.stringify(context)}`,
  ].join("\n");
}

/** 요청 검증. 잘못되면 이유 문자열, 괜찮으면 null. */
export function validateRequest(body: unknown): string | null {
  const b = body as Partial<IntentRequest> | null;
  if (!b || typeof b !== "object") return "JSON 본문이 필요합니다";
  if (typeof b.utterance !== "string" || !b.utterance.trim()) return "utterance가 필요합니다";
  if (b.utterance.length > INTENT_LIMITS.maxUtteranceChars) return "utterance가 너무 깁니다";
  if (!Array.isArray(b.history)) return "history 배열이 필요합니다";
  if (b.history.some((t) => !t || (t.role !== "user" && t.role !== "assistant") || typeof t.text !== "string")) {
    return "history 형식이 잘못됐습니다";
  }
  if (!b.context || !STAGES.includes(b.context.stage as Stage)) return "context.stage는 BEDTIME_PREP 또는 NIGHT여야 합니다";
  if (JSON.stringify(b).length > 4_000) return "요청이 너무 깁니다";
  return null;
}

/**
 * LLM 출력 검증. 형식이 틀린 제안·단계에서 허용되지 않은 제안은 버린다.
 * reply가 없거나 형식이 틀리면 null (앱은 "이해하지 못했어요"를 보여준다).
 */
export function validateResult(raw: unknown, stage: Stage): IntentResult | null {
  const r = raw as Partial<IntentResult> | null;
  if (!r || typeof r.reply !== "string" || !Array.isArray(r.proposals)) return null;
  const reply = r.reply.replace(/\s+/g, " ").trim();
  if (!reply || reply.length > INTENT_LIMITS.maxReplyChars) return null;

  const proposals = r.proposals
    .filter((p): p is Proposal => !!p && ALLOWED[stage].includes(p.type as ActionType))
    .filter(isWellFormed)
    .slice(0, INTENT_LIMITS.maxProposals);
  // 제안이 있었는데 모두 걸러졌으면 "바꿀까요?" 같은 답변을 그대로 보여주지 않는다
  if (r.proposals.length > 0 && proposals.length === 0) {
    return { reply: CANNOT_CHANGE_REPLY, needsClarification: false, proposals: [] };
  }
  return { reply, needsClarification: r.needsClarification === true && proposals.length === 0, proposals };
}

function isWellFormed(p: Proposal): boolean {
  const L = INTENT_LIMITS;
  switch (p.type) {
    case "SET_TEMPERATURE":
      return typeof p.celsius === "number" && p.celsius >= L.tempMinC && p.celsius <= L.tempMaxC;
    case "ADJUST_TEMPERATURE":
      return typeof p.deltaC === "number" && p.deltaC !== 0 && Math.abs(p.deltaC) <= L.maxDeltaC;
    case "SET_WAKE_TIME":
      return typeof p.time === "string" && /^([01]\d|2[0-3]):[0-5]\d$/.test(p.time);
    case "WAKE_AT_SUNRISE":
      return true;
    case "SET_THEME":
      return THEMES.includes(p.theme as (typeof THEMES)[number]);
    case "SET_SOUND":
    case "SET_VOICE":
      return typeof p.on === "boolean";
  }
}

export interface IntentDeps {
  apiKey: string | undefined;
  model: string;
  log?: (message: string) => void;
}

export async function interpret(request: IntentRequest, deps: IntentDeps): Promise<IntentResult | null> {
  const { apiKey, model = DEFAULT_MODEL, log = console.warn } = deps;
  if (!apiKey) {
    log("OPENAI_API_KEY가 없어 발화를 해석하지 않음");
    return null;
  }
  const client = new OpenAI({ apiKey, timeout: 10_000, maxRetries: 0 });
  const history = request.history.slice(-INTENT_LIMITS.maxHistoryTurns);
  try {
    const response = await client.responses.create({
      model,
      instructions: instructions(request.context),
      input: [
        ...history.map((t) => ({ role: t.role, content: t.text })),
        { role: "user" as const, content: request.utterance },
      ],
      reasoning: { effort: "low" },
      max_output_tokens: 2_000,
      text: { format: { type: "json_schema", name: "naru_intent", schema: INTENT_SCHEMA, strict: true } },
    });
    const result = validateResult(JSON.parse(response.output_text ?? "null"), request.context.stage);
    if (!result) log(`발화 해석 검증 실패: ${response.output_text}`);
    return result;
  } catch (e) {
    if (e instanceof OpenAI.APIError) log(`OpenAI 오류 (intent): ${e.status} ${e.message}`);
    else log(`발화 해석 실패: ${e}`);
    return null;
  }
}
