import { registerClient } from "@/lib/oauth";
import { rateLimit, clientIp, tooManyRequests } from "@/lib/rate-limit";

export const runtime = "nodejs";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
};

// RFC 7591 Dynamic Client Registration.
export async function POST(req: Request) {
  if (!rateLimit(`oauth-register:${clientIp(req)}`, 10, 60_000)) return tooManyRequests();

  let body: Record<string, unknown>;
  try {
    body = await req.json();
  } catch {
    return Response.json(
      { error: "invalid_client_metadata", error_description: "Body must be JSON" },
      { status: 400, headers: CORS },
    );
  }

  const redirectUris = body.redirect_uris;
  if (!Array.isArray(redirectUris) || redirectUris.length === 0 || redirectUris.length > 10) {
    return Response.json(
      { error: "invalid_redirect_uri", error_description: "redirect_uris is required (max 10)" },
      { status: 400, headers: CORS },
    );
  }

  // Each redirect_uri must be an absolute URL: https anywhere, http only for
  // loopback (dev). Blocks open-redirect / phishing client registrations.
  const isValidRedirect = (u: unknown): u is string => {
    if (typeof u !== "string") return false;
    let url: URL;
    try {
      url = new URL(u);
    } catch {
      return false;
    }
    if (url.protocol === "https:") return true;
    if (url.protocol === "http:" && (url.hostname === "localhost" || url.hostname === "127.0.0.1"))
      return true;
    return false;
  };
  if (!redirectUris.every(isValidRedirect)) {
    return Response.json(
      {
        error: "invalid_redirect_uri",
        error_description: "Each redirect_uri must be an absolute https URL (http only for localhost).",
      },
      { status: 400, headers: CORS },
    );
  }

  const client = await registerClient({
    clientName: typeof body.client_name === "string" ? body.client_name : "MCP Client",
    redirectUris: redirectUris as string[],
    tokenEndpointAuthMethod:
      typeof body.token_endpoint_auth_method === "string"
        ? body.token_endpoint_auth_method
        : "none",
  });

  return Response.json(
    {
      client_id: client.clientId,
      ...(client.clientSecret ? { client_secret: client.clientSecret } : {}),
      client_name: client.clientName,
      redirect_uris: client.redirectUris,
      grant_types: ["authorization_code", "refresh_token"],
      response_types: ["code"],
      token_endpoint_auth_method: client.clientSecret ? "client_secret_post" : "none",
    },
    { status: 201, headers: CORS },
  );
}

export function OPTIONS() {
  return new Response(null, { headers: CORS });
}
