import http from "k6/http";

export const options = { vus: 5, duration: "30s" };

export default function () {
  http.post(
    `${__ENV.BASE_URL}/conversations/${__ENV.CONVERSATION_ID}/messages`,
    JSON.stringify({ content: "warm-up", type: "TEXT", replyToMessageId: null, attachments: [] }),
    { headers: { Authorization: `Bearer ${__ENV.TOKEN}`, "Content-Type": "application/json" } },
  );
}
