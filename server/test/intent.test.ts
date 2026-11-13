import { test } from "node:test";
import assert from "node:assert/strict";
import { ALLOWED, INTENT_SCHEMA, instructions, interpret, validateRequest, validateResult, type IntentContext } from "../lib/intent.js";

const bedtime: IntentContext = {
  stage: "BEDTIME_PREP", targetTempC: 22, tempMinC: 15, tempMaxC: 30, wakeAt: "07:00", sunrise: "07:10", theme: "FOREST", soundOn: true, voiceOn: true,
};
const p = (o: Partial<Record<string, unknown>>) => ({ type: "SET_TEMPERATURE", celsius: null, deltaC: null, time: null, theme: null, on: null, ...o });

test("형식이 맞는 제안만 남긴다", () => {
  const result = validateResult(
    {
      reply: "23도로 바꿀까요?",
      needsClarification: false,
      proposals: [p({ celsius: 23 }), p({ type: "SET_WAKE_TIME", time: "7:30" }), p({ type: "SET_THEME", theme: "DESERT" })],
    },
    "BEDTIME_PREP",
  );
  assert.deepEqual(result?.proposals.map((x) => x.type), ["SET_TEMPERATURE"]);
});

test("단계에서 허용되지 않은 동작은 버린다 (밤에는 기상 시각 변경 불가)", () => {
  const result = validateResult({ reply: "7시 반으로 바꿀까요?", needsClarification: false, proposals: [p({ type: "SET_WAKE_TIME", time: "07:30" })] }, "NIGHT");
  assert.deepEqual(result?.proposals, []);
});

test("온도 범위·변화량을 검사한다", () => {
  const r = (o: object) => validateResult({ reply: "네", needsClarification: false, proposals: [p(o)] }, "BEDTIME_PREP")!.proposals.length;
  assert.equal(r({ celsius: 50 }), 0);
  assert.equal(r({ type: "ADJUST_TEMPERATURE", deltaC: 0 }), 0);
  assert.equal(r({ type: "ADJUST_TEMPERATURE", deltaC: 9 }), 0);
  assert.equal(r({ type: "ADJUST_TEMPERATURE", deltaC: -1 }), 1);
});

test("되묻기는 제안이 없을 때만 true", () => {
  assert.equal(validateResult({ reply: "몇 시에 깨워 드릴까요?", needsClarification: true, proposals: [] }, "BEDTIME_PREP")?.needsClarification, true);
  assert.equal(validateResult({ reply: "네", needsClarification: true, proposals: [p({ celsius: 23 })] }, "BEDTIME_PREP")?.needsClarification, false);
});

test("답변이 없거나 너무 길면 실패", () => {
  assert.equal(validateResult({ reply: "", needsClarification: false, proposals: [] }, "NIGHT"), null);
  assert.equal(validateResult({ reply: "가".repeat(121), needsClarification: false, proposals: [] }, "NIGHT"), null);
  assert.equal(validateResult(null, "NIGHT"), null);
});

test("요청 검증", () => {
  const ok = { utterance: "따뜻하게", history: [], context: bedtime };
  assert.equal(validateRequest(ok), null);
  assert.ok(validateRequest({ ...ok, utterance: "가".repeat(101) }));
  assert.ok(validateRequest({ ...ok, history: [{ role: "system", text: "x" }] }));
  assert.ok(validateRequest({ ...ok, context: { ...bedtime, stage: "MORNING" } }));
});

test("지시문에는 지금 단계의 동작만 있다", () => {
  const night = instructions({ ...bedtime, stage: "NIGHT" });
  assert.ok(!night.includes("SET_WAKE_TIME"));
  assert.ok(night.includes("SET_SOUND"));
  assert.deepEqual(ALLOWED.NIGHT.includes("SET_WAKE_TIME"), false);
});

test("스키마는 strict 조건(모든 필드 필수, 추가 필드 금지)을 지킨다", () => {
  const item = INTENT_SCHEMA.properties.proposals.items;
  assert.deepEqual([...item.required].sort(), Object.keys(item.properties).sort());
  assert.equal(item.additionalProperties, false);
});

test("키가 없으면 호출하지 않고 null", async () => {
  assert.equal(await interpret({ utterance: "x", history: [], context: bedtime }, { apiKey: undefined, model: "m", log: () => {} }), null);
});
