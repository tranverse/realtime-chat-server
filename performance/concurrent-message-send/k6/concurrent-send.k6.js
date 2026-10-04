import http from "k6/http";
import exec from "k6/execution";
import { check } from "k6";
import { Counter } from "k6/metrics";

const concurrency = Number(__ENV.CONCURRENCY);
const successfulRequests = new Counter("successful_requests");
const failedRequests = new Counter("failed_requests");

export const options = {
  scenarios: {
    concurrent_send: {
      executor: "shared-iterations",
      vus: concurrency,
      iterations: concurrency,
      maxDuration: "60s",
    },
  },
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
};

export default function () {
  const payload = JSON.stringify({
    content: `benchmark-${__ENV.RUN_ID}-${exec.scenario.iterationInTest}-${exec.vu.idInTest}`,
    type: "TEXT",
    replyToMessageId: null,
    attachments: [],
  });
  const response = http.post(
    `${__ENV.BASE_URL}/conversations/${__ENV.CONVERSATION_ID}/messages`,
    payload,
    { headers: { Authorization: `Bearer ${__ENV.TOKEN}`, "Content-Type": "application/json" } },
  );
  const successful = response.status === 201;
  if (successful) successfulRequests.add(1);
  else {
    failedRequests.add(1);
    console.error(`status=${response.status} body=${response.body}`);
  }
  check(response, { "message created": () => successful });
}
