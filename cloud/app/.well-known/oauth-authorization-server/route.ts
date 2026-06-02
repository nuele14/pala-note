import { getPublicOrigin } from "mcp-handler";
import { authorizationServerMetadata } from "@/lib/oauth";

export const runtime = "nodejs";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, OPTIONS",
  "Access-Control-Allow-Headers": "*",
};

export function GET(req: Request) {
  const origin = getPublicOrigin(req);
  return Response.json(authorizationServerMetadata(origin), {
    headers: { ...CORS, "Cache-Control": "public, max-age=3600" },
  });
}

export function OPTIONS() {
  return new Response(null, { headers: CORS });
}
