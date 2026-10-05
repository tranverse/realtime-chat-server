import http from "k6/http";

const dataset = Number(__ENV.DATASET);
const positions = [0, Math.floor(dataset * 0.5), Math.floor(dataset * 0.9)];

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.WARMUP_DURATION || "30s",
};

export default function () {
  for (const offset of positions) {
    const beforeSequence = dataset - offset + 1;
    const base = `${__ENV.BASE_URL}/benchmark/message-history/${__ENV.CONVERSATION_ID}`;
    const params = { headers: { Authorization: `Bearer ${__ENV.TOKEN}` } };
    http.get(`${base}/keyset?beforeSequence=${beforeSequence}&size=50`, params);
    http.get(`${base}/offset?offset=${offset}&size=50`, params);
  }
}
