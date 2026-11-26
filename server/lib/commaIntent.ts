import OpenAI from "openai";
import { DEFAULT_MODEL } from "./narration.js";

export const COMMA_STAGES = ["READY", "RESTING", "WAKING"] as const;
export type CommaStage = (typeof COMMA_STAGES)[number];

export const COMMA_ACTIONS = [
  "SET_DURATION",
  "SET_TEMPERATURE",
  "ADJUST_TEMPERATURE",
  "SET_REST_SOUND",
  "SET_SCREEN_BRIGHTNESS",
  "SET_AMBIENT_LIGHT",
  "EXTEND_REST",
] as const;
export type CommaAction = (typeof COMMA_ACTIONS)[number];

const ALLOWED: Record<CommaStage, CommaAction[]> = {
  READY: ["SET_DURATION", "SET_TEMPERATURE", "ADJUST_TEMPERATURE", "SET_REST_SOUND", "SET_SCREEN_BRIGHTNESS", "SET_AMBIENT_LIGHT"],
  RESTING: ["SET_TEMPERATURE", "ADJUST_TEMPERATURE", "SET_REST_SOUND", "SET_SCREEN_BRIGHTNESS", "SET_AMBIENT_LIGHT", "EXTEND_REST"],
  WAKING: ["EXTEND_REST"],
};

export const COMMA_SOUNDS = ["SILENT", "RAIN", "OCEAN", "FOREST", "LAKE"] as const;
export type CommaSound = (typeof COMMA_SOUNDS)[number];

export interface CommaContext {
  stage: CommaStage;
  sessionId: string | null;
  selectedMinutes: number;
  remainingMinutes: number | null;
  currentTempC: number | null;
  recommendedTempC: number | null;
  tempMinC: number | null;
  tempMaxC: number | null;
  climateSupported: boolean;
  ambientLightSupported: boolean;
  sound: CommaSound;
  screenBrightnessPercent: number;
  recentPreferenceSummary: string | null;
}

export interface CommaRequest {
  requestId: string;
  utterance: string;
  context: CommaContext;
}

export interface CommaProposal {
  type: CommaAction;
  minutes: number | null;
  celsius: number | null;
  deltaC: number | null;
  sound: CommaSound | null;
  percent: number | null;
}

export interface CommaResult {
  requestId: string;
  reply: string;
  needsClarification: boolean;
  proposals: CommaProposal[];
}

const LIMITS = { maxUtterance: 120, maxReply: 160, maxProposals: 4, maxSummary: 240 };

export const COMMA_SCHEMA = {
  type: "object",
  additionalProperties: false,
  required: ["reply", "needsClarification", "proposals"],
  properties: {
    reply: { type: "string" },
    needsClarification: { type: "boolean" },
    proposals: {
      type: "array",
      maxItems: LIMITS.maxProposals,
      items: {
        type: "object",
        additionalProperties: false,
        required: ["type", "minutes", "celsius", "deltaC", "sound", "percent"],
        properties: {
          type: { type: "string", enum: [...COMMA_ACTIONS] },
          minutes: { type: ["integer", "null"] },
          celsius: { type: ["number", "null"] },
          deltaC: { type: ["number", "null"] },
          sound: { type: ["string", "null"], enum: [...COMMA_SOUNDS, null] },
          percent: { type: ["integer", "null"] },
        },
      },
    },
  },
} as const;

export function validateCommaRequest(raw: unknown): string | null {
  const r = raw as Partial<CommaRequest> | null;
  if (!r || typeof r !== "object") return "JSON 본문이 필요합니다";
  if (typeof r.requestId !== "string" || !r.requestId || r.requestId.length > 80) return "requestId가 잘못됐습니다";
  if (typeof r.utterance !== "string" || !r.utterance.trim() || r.utterance.length > LIMITS.maxUtterance) return "utterance가 잘못됐습니다";
  const c = r.context;
  if (!c || !COMMA_STAGES.includes(c.stage as CommaStage)) return "context.stage가 잘못됐습니다";
  if (!Number.isInteger(c.selectedMinutes) || c.selectedMinutes! < 1 || c.selectedMinutes! > 180) return "selectedMinutes가 잘못됐습니다";
  if (typeof c.recentPreferenceSummary === "string" && c.recentPreferenceSummary.length > LIMITS.maxSummary) return "이력 요약이 너무 깁니다";
  if (JSON.stringify(raw).length > 4_000) return "요청이 너무 깁니다";
  return null;
}

export function validateCommaResult(raw: unknown, request: CommaRequest): CommaResult | null {
  const r = raw as Partial<Omit<CommaResult, "requestId">> | null;
  if (!r || typeof r.reply !== "string" || !Array.isArray(r.proposals)) return null;
  const reply = r.reply.replace(/\s+/g, " ").trim();
  if (!reply || reply.length > LIMITS.maxReply) return null;
  const proposals = r.proposals
    .filter((p): p is CommaProposal => !!p && ALLOWED[request.context.stage].includes(p.type as CommaAction))
    .filter((p) => validProposal(p, request.context))
    .slice(0, LIMITS.maxProposals);
  if (r.proposals.length > 0 && proposals.length === 0) {
    return { requestId: request.requestId, reply: "현재 상태나 차량 지원 범위에서는 그 설정을 제안할 수 없어요.", needsClarification: false, proposals: [] };
  }
  return {
    requestId: request.requestId,
    reply,
    needsClarification: r.needsClarification === true && proposals.length === 0,
    proposals,
  };
}

function validProposal(p: CommaProposal, c: CommaContext): boolean {
  switch (p.type) {
    case "SET_DURATION":
      return c.stage === "READY" && Number.isInteger(p.minutes) && p.minutes! >= 1 && p.minutes! <= 180;
    case "EXTEND_REST":
      return (c.stage === "RESTING" || c.stage === "WAKING") && (p.minutes === 5 || p.minutes === 10);
    case "SET_TEMPERATURE":
      return c.climateSupported && typeof p.celsius === "number" && c.tempMinC != null && c.tempMaxC != null && p.celsius >= c.tempMinC && p.celsius <= c.tempMaxC;
    case "ADJUST_TEMPERATURE":
      return c.climateSupported && typeof p.deltaC === "number" && p.deltaC !== 0 && Math.abs(p.deltaC) <= 5;
    case "SET_REST_SOUND":
      return COMMA_SOUNDS.includes(p.sound as CommaSound);
    case "SET_SCREEN_BRIGHTNESS":
      return Number.isInteger(p.percent) && p.percent! >= 5 && p.percent! <= 100;
    case "SET_AMBIENT_LIGHT":
      return c.ambientLightSupported && Number.isInteger(p.percent) && p.percent! >= 0 && p.percent! <= 100;
  }
}

function commaInstructions(context: CommaContext): string {
  return [
    "너는 차량 휴식 앱 나루의 쉼표 모드 설정 제안기다.",
    "출력은 실행 명령이 아니라 사용자가 승인할 구조화된 제안이다. 적용했다고 말하지 말고 바꿀지 묻는다.",
    "휴식 효과나 안전을 보장하지 않는다.",
    "복합 요청은 서로 충돌하지 않는 제안을 최대 4개까지 만든다.",
    "조금 따뜻하게/시원하게는 ADJUST_TEMPERATURE ±1, 정확한 온도는 SET_TEMPERATURE다.",
    "소리 없이는 SILENT, 빗소리는 RAIN이다. 연장은 5분 또는 10분만 허용한다.",
    `현재 단계 허용 액션: ${ALLOWED[context.stage].join(", ")}`,
    `현재 컨텍스트: ${JSON.stringify(context)}`,
  ].join("\n");
}

export async function interpretComma(request: CommaRequest, deps: { apiKey?: string; model?: string; log?: (message: string) => void }): Promise<CommaResult | null> {
  const log = deps.log ?? console.warn;
  if (!deps.apiKey) {
    log("OPENAI_API_KEY가 없어 쉼표 발화를 해석하지 않음");
    return null;
  }
  try {
    const client = new OpenAI({ apiKey: deps.apiKey, timeout: 10_000, maxRetries: 0 });
    const response = await client.responses.create({
      model: deps.model ?? DEFAULT_MODEL,
      instructions: commaInstructions(request.context),
      input: request.utterance,
      reasoning: { effort: "low" },
      max_output_tokens: 1_500,
      text: { format: { type: "json_schema", name: "naru_comma_intent", schema: COMMA_SCHEMA, strict: true } },
    });
    return validateCommaResult(JSON.parse(response.output_text ?? "null"), request);
  } catch (error) {
    log(`쉼표 발화 해석 실패: ${error}`);
    return null;
  }
}
