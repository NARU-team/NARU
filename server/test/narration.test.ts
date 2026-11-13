import { test } from "node:test";
import assert from "node:assert/strict";
import { narrate, numbersIn, validateOutput, validateRequest, instructions } from "../lib/narration.js";

const facts = { place: "태안 꽃지해수욕장", grade: "주의·이동 권장", reasons: ["밤사이 06:09에 만조예요"], minTempC: 13 };

test("사실에 있는 숫자만 쓴 문장은 통과", () => {
  assert.equal(
    validateOutput("태안 꽃지해수욕장은 주의·이동 권장이에요. 밤사이 6시 9분에 만조라 이동을 권해요. 최저 13도예요.", facts),
    "태안 꽃지해수욕장은 주의·이동 권장이에요. 밤사이 6시 9분에 만조라 이동을 권해요. 최저 13도예요.",
  );
});

test("사실에 없는 숫자를 만들면 실패", () => {
  assert.equal(validateOutput("최저기온은 5도예요.", facts), null);
});

test("아침 브리핑은 4문장까지 허용한다", () => {
  const morning = { 브리핑: ["배터리는 68% 남았어요.", "밤사이 0%를 썼어요.", "지금 바깥은 비, 13°C예요.", "차 안은 19.1°C예요."] };
  const text = "배터리는 68% 남았어요. 밤사이 0%를 썼어요. 바깥은 비가 오고 13°C예요. 차 안은 19.1°C예요.";
  assert.equal(validateOutput(text, morning, "MORNING"), text);
  assert.equal(validateOutput(text, morning, "ARRIVAL"), null);
});

test("잠들기 전은 배터리 주의까지 5문장을 허용한다", () => {
  const bedtime = { 설정온도C: "24", 기상시각: "07:00", 아침예상잔량퍼센트: "12~18", 주의: "아침에 배터리가 20%보다 낮아질 수 있으니 설정 온도를 조금 낮추는 걸 권해요." };
  const text = "오늘 밤은 숲 테마에 설정온도 24도로 맞춰져 있습니다. 기상시각은 07:00이고, 아침 예상 잔량은 12~18%입니다. 아침에 배터리가 20%보다 낮아질 수 있어요. 설정 온도를 조금 낮추는 걸 권해요. 편히 쉬세요.";
  assert.equal(validateOutput(text, bedtime, "BEDTIME"), text);
});

test("소수점은 문장 끝으로 세지 않는다", () => {
  assert.equal(validateOutput("차 안은 19.1°C예요. 바깥은 13.5°C예요. 편히 쉬세요.", { a: "19.1 13.5" }, "BEDTIME"), "차 안은 19.1°C예요. 바깥은 13.5°C예요. 편히 쉬세요.");
});

test("문장이 너무 많거나 길면 실패", () => {
  assert.equal(validateOutput("하나. 둘. 셋. 넷.", facts), null);
  assert.equal(validateOutput("가".repeat(181), facts), null);
});

test("마크다운·이모지·괄호는 실패 (소리 내어 읽는 문장)", () => {
  assert.equal(validateOutput("**주의**가 필요해요.", facts), null);
  assert.equal(validateOutput("잘 자요 🌙", facts), null);
  assert.equal(validateOutput("만조(06:09)예요.", facts), null);
});

test("빈 출력은 실패", () => {
  assert.equal(validateOutput("   ", facts), null);
});

test("숫자 추출은 앞자리 0과 부호를 무시한다", () => {
  assert.deepEqual(numbersIn("06:09, -2도, 13.5"), ["6", "9", "2", "13.5"]);
});

test("요청 검증", () => {
  assert.equal(validateRequest({ moment: "ARRIVAL", facts: {} }), null);
  assert.ok(validateRequest({ moment: "NOON", facts: {} }));
  assert.ok(validateRequest({ moment: "MORNING" }));
  assert.ok(validateRequest({ moment: "MORNING", facts: [] }));
  assert.ok(validateRequest(null));
});

test("잠들기 전 지시문은 예상 잔량과 주의를 빼지 말라고 한다", () => {
  assert.match(instructions("BEDTIME"), /아침 예상 잔량/);
  assert.match(instructions("BEDTIME"), /빼지 말고 꼭 전한다/);
});

test("등급을 약하게 바꾸지 말라는 규칙이 지시문에 있다", () => {
  assert.match(instructions("ARRIVAL"), /약하게 바꾸지 않는다/);
});

test("키가 없으면 호출하지 않고 null", async () => {
  assert.equal(await narrate("BEDTIME", {}, { apiKey: undefined, model: "x", log: () => {} }), null);
});
