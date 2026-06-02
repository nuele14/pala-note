import {
  getClient,
  consumeAuthCode,
  issueTokenPair,
  rotateRefreshToken,
  verifyPkce,
  verifyClientSecret,
} from "@/lib/oauth";
import { rateLimit, clientIp, tooManyRequests } from "@/lib/rate-limit";

export const runtime = "nodejs";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
};

function err(error: string, status = 400, description?: string) {
  return Response.json(
    { error, ...(description ? { error_description: description } : {}) },
    { status, headers: CORS },
  );
}

// OAuth 2.1 token endpoint: authorization_code (PKCE) + refresh_token grants.
export async function POST(req: Request) {
  if (!rateLimit(`oauth-token:${clientIp(req)}`, 30, 60_000)) return tooManyRequests();

  let form: URLSearchParams;
  try {
    const ct = req.headers.get("content-type") ?? "";
    if (ct.includes("application/json")) {
      const j = await req.json();
      form = new URLSearchParams(j as Record<string, string>);
    } else {
      form = new URLSearchParams(await req.text());
    }
  } catch {
    return err("invalid_request", 400, "Could not parse body");
  }

  const grantType = form.get("grant_type");
  const clientId = form.get("client_id");
  const clientSecret = form.get("client_secret") ?? undefined;
  if (!clientId) return err("invalid_client", 401, "client_id required");

  const client = await getClient(clientId);
  if (!client) return err("invalid_client", 401, "Unknown client");
  if (!verifyClientSecret(client.clientSecretHash, clientSecret)) {
    return err("invalid_client", 401, "Bad client credentials");
  }

  const now = Date.now();

  if (grantType === "authorization_code") {
    const code = form.get("code");
    const redirectUri = form.get("redirect_uri");
    const codeVerifier = form.get("code_verifier");
    if (!code || !redirectUri || !codeVerifier) {
      return err("invalid_request", 400, "code, redirect_uri, code_verifier required");
    }
    const row = await consumeAuthCode(code, now);
    if (!row) return err("invalid_grant", 400, "Invalid or expired code");
    if (row.clientId !== clientId || row.redirectUri !== redirectUri) {
      return err("invalid_grant", 400, "Code does not match client/redirect_uri");
    }
    if (!verifyPkce(codeVerifier, row.codeChallenge, row.codeChallengeMethod)) {
      return err("invalid_grant", 400, "PKCE verification failed");
    }
    const tokens = await issueTokenPair({
      clientId,
      tenantId: row.tenantId,
      scope: row.scope,
      now,
    });
    return Response.json(tokens, { headers: CORS });
  }

  if (grantType === "refresh_token") {
    const refreshToken = form.get("refresh_token");
    if (!refreshToken) return err("invalid_request", 400, "refresh_token required");
    const tokens = await rotateRefreshToken(refreshToken, clientId, now);
    if (!tokens) return err("invalid_grant", 400, "Invalid or expired refresh token");
    return Response.json(tokens, { headers: CORS });
  }

  return err("unsupported_grant_type", 400);
}

export function OPTIONS() {
  return new Response(null, { headers: CORS });
}
