import { test } from "node:test";
import assert from "node:assert/strict";
import { COMMA_SCHEMA, validateCommaRequest, validateCommaResult, type CommaRequest, type CommaProposal } from "../lib/commaIntent.js";

const request: CommaRequest = {
  requestId: "request-1",
  utterance: "20분 동안 따뜻하게 빗소리 들으면서 쉴래",
  context: {
    stage: "READY",
    sessionId: null,
    selectedMinutes: 15,
    remainingMinutes: null,
    currentTempC: 23,
    recommendedTempC: 24,
    tempMinC: 16,
    tempMaxC: 30,
    climateSupported: true,
    ambientLightSupported: true,
    sound: "SILENT",
    screenBrightnessPercent: 30,
    recentPreferenceSummary: "최근 편안함 24도",
  },
};
const proposal = (value: Partial<CommaProposal>): CommaProposal => ({
  type: "SET_DURATION", minutes: null, celsius: null, deltaC: null, sound: null, percent: null, ...value,
});

test("복합 요청의 허용된 제안을 보존한다", () => {
  const result = validateCommaResult({
    reply: "20분, 1도 높게, 빗소리로 바꿀까요?",
    needsClarification: false,
    proposals: [
      proposal({ type: "SET_DURATION", minutes: 20 }),
      proposal({ type: "ADJUST_TEMPERATURE", deltaC: 1 }),
      proposal({ type: "SET_REST_SOUND", sound: "RAIN" }),
    ],
  }, request);
  assert.deepEqual(result?.proposals.map((p) => p.type), ["SET_DURATION", "ADJUST_TEMPERATURE", "SET_REST_SOUND"]);
  assert.equal(result?.requestId, request.requestId);
});

test("단계, capability, 값 범위를 서버에서도 제한한다", () => {
  const resting = { ...request, context: { ...request.context, stage: "RESTING" as const, climateSupported: false, ambientLightSupported: false } };
  const result = validateCommaResult({
    reply: "변경할까요?", needsClarification: false,
    proposals: [
      proposal({ type: "SET_DURATION", minutes: 20 }),
      proposal({ type: "SET_TEMPERATURE", celsius: 25 }),
      proposal({ type: "SET_AMBIENT_LIGHT", percent: 20 }),
      proposal({ type: "EXTEND_REST", minutes: 5 }),
    ],
  }, resting);
  assert.deepEqual(result?.proposals.map((p) => p.type), ["EXTEND_REST"]);
});

test("요청에는 원문, 요청 ID, 최소 컨텍스트가 필요하다", () => {
  assert.equal(validateCommaRequest(request), null);
  assert.ok(validateCommaRequest({ ...request, requestId: "" }));
  assert.ok(validateCommaRequest({ ...request, utterance: "" }));
  assert.ok(validateCommaRequest({ ...request, context: { ...request.context, stage: "FINISHED" } }));
});

test("strict 스키마는 모든 제안 필드를 요구한다", () => {
  const item = COMMA_SCHEMA.properties.proposals.items;
  assert.deepEqual([...item.required].sort(), Object.keys(item.properties).sort());
  assert.equal(item.additionalProperties, false);
});
