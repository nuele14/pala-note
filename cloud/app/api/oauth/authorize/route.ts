import { getPublicOrigin } from "mcp-handler";
import { auth } from "@/auth";
import {
  getClient,
  issueAuthCode,
  consentToken,
  verifyConsentToken,
  OAUTH_SCOPE,
} from "@/lib/oauth";

export const runtime = "nodejs";

function errorRedirect(redirectUri: string, error: string, state: string | null) {
  const u = new URL(redirectUri);
  u.searchParams.set("error", error);
  if (state) u.searchParams.set("state", state);
  return Response.redirect(u.toString(), 302);
}

function esc(s: string): string {
  return s.replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]!,
  );
}

interface AuthzParams {
  clientId: string;
  redirectUri: string;
  responseType: string;
  state: string | null;
  codeChallenge: string;
  codeChallengeMethod: string;
  scope: string;
}

function readParams(sp: URLSearchParams): AuthzParams {
  return {
    clientId: sp.get("client_id") ?? "",
    redirectUri: sp.get("redirect_uri") ?? "",
    responseType: sp.get("response_type") ?? "",
    state: sp.get("state"),
    codeChallenge: sp.get("code_challenge") ?? "",
    codeChallengeMethod: sp.get("code_challenge_method") ?? "S256",
    scope: sp.get("scope") || OAUTH_SCOPE,
  };
}

// Validate the request against a registered client. Returns an error response,
// or the client name on success.
async function validate(
  p: AuthzParams,
): Promise<{ ok: true; clientName: string } | { ok: false; res: Response }> {
  if (!p.clientId || !p.redirectUri) {
    return { ok: false, res: new Response("invalid_request: client_id and redirect_uri required", { status: 400 }) };
  }
  const client = await getClient(p.clientId);
  if (!client || !client.redirectUris.includes(p.redirectUri)) {
    return { ok: false, res: new Response("invalid_request: unknown client or redirect_uri", { status: 400 }) };
  }
  // From here, OAuth errors go back to the client.
  if (p.responseType !== "code")
    return { ok: false, res: errorRedirect(p.redirectUri, "unsupported_response_type", p.state) };
  if (!p.codeChallenge || p.codeChallengeMethod !== "S256")
    return { ok: false, res: errorRedirect(p.redirectUri, "invalid_request", p.state) };
  return { ok: true, clientName: client.clientName };
}

// GET: show a consent screen (or bounce through login). Never issues a code.
export async function GET(req: Request) {
  const origin = getPublicOrigin(req);
  const url = new URL(req.url);
  const p = readParams(url.searchParams);

  const v = await validate(p);
  if (!v.ok) return v.res;

  const session = await auth();
  const tenantId = session?.user?.tenantId;
  if (!tenantId) {
    const login = new URL("/login", origin);
    login.searchParams.set("callbackUrl", url.pathname + url.search);
    return Response.redirect(login.toString(), 302);
  }

  const token = consentToken({
    tenantId,
    clientId: p.clientId,
    redirectUri: p.redirectUri,
    codeChallenge: p.codeChallenge,
  });
  const redirectHost = new URL(p.redirectUri).host;
  const email = session?.user?.email ?? "";

  const hidden = (name: string, value: string) =>
    `<input type="hidden" name="${esc(name)}" value="${esc(value)}">`;

  const html = `<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Authorize ${esc(v.clientName)}</title>
<style>
  :root{--coral:oklch(0.645 0.196 41.5)}
  body{font-family:system-ui,sans-serif;background:#faf9f8;margin:0;display:flex;min-height:100vh;align-items:center;justify-content:center;color:#1c1917}
  .card{background:#fff;border:1px solid #e7e5e4;border-radius:16px;max-width:400px;width:calc(100% - 32px);padding:28px;box-shadow:0 8px 30px rgba(0,0,0,.06)}
  .dot{width:32px;height:32px;border-radius:8px;background:var(--coral);display:flex;align-items:center;justify-content:center;margin-bottom:16px}
  .dot span{width:8px;height:8px;border-radius:9999px;background:#fff}
  h1{font-size:19px;margin:0 0 6px}
  p{color:#57534e;font-size:14px;line-height:1.5;margin:0 0 8px}
  .who{font-size:12px;color:#78716c;margin-bottom:18px}
  ul{margin:14px 0 18px;padding-left:18px;color:#44403c;font-size:14px}
  .row{display:flex;gap:10px;margin-top:20px}
  button,a.btn{flex:1;text-align:center;padding:11px;border-radius:9px;font-size:14px;font-weight:600;cursor:pointer;border:0;text-decoration:none}
  .allow{background:var(--coral);color:#fff}
  .deny{background:#f5f5f4;color:#44403c;border:1px solid #e7e5e4}
  code{background:#f5f5f4;padding:1px 5px;border-radius:4px;font-size:12px}
</style></head><body>
<div class="card">
  <div class="dot"><span></span></div>
  <h1>Authorize ${esc(v.clientName)}</h1>
  <p><strong>${esc(v.clientName)}</strong> wants to connect to your Pala Note workspace.</p>
  <div class="who">Signed in as ${esc(email)}</div>
  <p>It will be able to:</p>
  <ul><li>Read and search your voice notes</li><li>Read your workspace instructions</li></ul>
  <p class="who">Redirects to <code>${esc(redirectHost)}</code></p>
  <form method="POST" action="/api/oauth/authorize">
    ${hidden("client_id", p.clientId)}
    ${hidden("redirect_uri", p.redirectUri)}
    ${hidden("response_type", "code")}
    ${hidden("code_challenge", p.codeChallenge)}
    ${hidden("code_challenge_method", "S256")}
    ${hidden("scope", p.scope)}
    ${p.state ? hidden("state", p.state) : ""}
    ${hidden("consent", token)}
    <div class="row">
      <a class="btn deny" href="${esc((() => { const u = new URL(p.redirectUri); u.searchParams.set("error", "access_denied"); if (p.state) u.searchParams.set("state", p.state); return u.toString(); })())}">Cancel</a>
      <button class="allow" type="submit">Authorize</button>
    </div>
  </form>
</div></body></html>`;

  return new Response(html, { headers: { "Content-Type": "text/html; charset=utf-8" } });
}

// POST: the user clicked Authorize. Requires a valid session + consent token.
export async function POST(req: Request) {
  const form = await req.formData();
  const p: AuthzParams = {
    clientId: String(form.get("client_id") ?? ""),
    redirectUri: String(form.get("redirect_uri") ?? ""),
    responseType: String(form.get("response_type") ?? ""),
    state: form.get("state") ? String(form.get("state")) : null,
    codeChallenge: String(form.get("code_challenge") ?? ""),
    codeChallengeMethod: String(form.get("code_challenge_method") ?? "S256"),
    scope: String(form.get("scope") || OAUTH_SCOPE),
  };
  const consent = String(form.get("consent") ?? "");

  const v = await validate(p);
  if (!v.ok) return v.res;

  // SameSite=lax means a cross-site POST won't carry the session cookie, so this
  // already fails for forged submits; the consent token is the explicit guard.
  const session = await auth();
  const tenantId = session?.user?.tenantId;
  if (!tenantId) return new Response("not authenticated", { status: 401 });

  const okConsent = verifyConsentToken(consent, {
    tenantId,
    clientId: p.clientId,
    redirectUri: p.redirectUri,
    codeChallenge: p.codeChallenge,
  });
  if (!okConsent) return new Response("invalid consent", { status: 400 });

  const code = await issueAuthCode({
    clientId: p.clientId,
    tenantId,
    redirectUri: p.redirectUri,
    codeChallenge: p.codeChallenge,
    codeChallengeMethod: "S256",
    scope: p.scope,
    now: Date.now(),
  });

  const back = new URL(p.redirectUri);
  back.searchParams.set("code", code);
  if (p.state) back.searchParams.set("state", p.state);
  return Response.redirect(back.toString(), 302);
}
