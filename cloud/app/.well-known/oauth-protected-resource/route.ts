import {
  protectedResourceHandler,
  metadataCorsOptionsRequestHandler,
  getPublicOrigin,
} from "mcp-handler";

export const runtime = "nodejs";

export function GET(req: Request) {
  const origin = getPublicOrigin(req);
  return protectedResourceHandler({
    authServerUrls: [origin],
    resourceUrl: `${origin}/mcp`,
  })(req);
}

export const OPTIONS = metadataCorsOptionsRequestHandler();
