import http from "k6/http";
import { check } from "k6";

const dataset = Number(__ENV.DATASET);
const pageSize = Number(__ENV.PAGE_SIZE || 50);
const strategy = __ENV.STRATEGY;
const position = __ENV.POSITION;
const offsetByPosition = {
  shallow: 0,
  middle: Math.floor(dataset * 0.5),
  deep: Math.floor(dataset * 0.9),
};
const offset = offsetByPosition[position];
const beforeSequence = dataset - offset + 1;

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || "10s",
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
  },
};

export default function () {
  const query = strategy === "keyset"
    ? `beforeSequence=${beforeSequence}&size=${pageSize}`
    : `offset=${offset}&size=${pageSize}`;
  const url = `${__ENV.BASE_URL}/benchmark/message-history/${__ENV.CONVERSATION_ID}/${strategy}?${query}`;
  const response = http.get(url, {
    headers: { Authorization: `Bearer ${__ENV.TOKEN}` },
    tags: { dataset: String(dataset), position, strategy },
  });
  check(response, {
    "status is 200": (result) => result.status === 200,
    "page contains 50 messages": (result) => {
      try {
        return result.json("data").length === pageSize;
      } catch (_) {
        return false;
      }
    },
  });
}
