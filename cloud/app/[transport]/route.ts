import { createMcpHandler, withMcpAuth } from "mcp-handler";
import { z } from "zod";
import { validateAccessToken } from "@/lib/oauth";
import { listNotes, getNote, searchNotes, listTags, getInstructions } from "@/lib/notes-queries";

export const runtime = "nodejs";

function tenantOf(extra: { authInfo?: { extra?: Record<string, unknown> } }): string {
  const tenantId = extra.authInfo?.extra?.tenantId;
  if (typeof tenantId !== "string") throw new Error("missing tenant context");
  return tenantId;
}

function json(data: unknown) {
  return { content: [{ type: "text" as const, text: JSON.stringify(data, null, 2) }] };
}

const handler = createMcpHandler(
  (server) => {
    server.registerTool(
      "list_notes",
      {
        description:
          "List the user's voice notes, newest first. Optionally filter by tag or a since-date (ISO 8601), and paginate with cursor.",
        inputSchema: {
          tag: z.string().optional(),
          since: z.string().optional(),
          limit: z.number().int().min(1).max(100).optional(),
          cursor: z.string().optional(),
        },
      },
      async (args, extra) => json(await listNotes(tenantOf(extra), args)),
    );

    server.registerTool(
      "get_note",
      {
        description: "Fetch the full text and metadata of a single note by its id.",
        inputSchema: { id: z.string() },
      },
      async (args, extra) => {
        const note = await getNote(tenantOf(extra), args.id);
        return note ? json(note) : json({ error: "not_found" });
      },
    );

    server.registerTool(
      "search_notes",
      {
        description: "Full-text (substring) search across the user's notes.",
        inputSchema: {
          query: z.string().min(1),
          limit: z.number().int().min(1).max(100).optional(),
        },
      },
      async (args, extra) => json(await searchNotes(tenantOf(extra), args.query, args.limit)),
    );

    server.registerTool(
      "list_tags",
      { description: "List the user's note tags with counts.", inputSchema: {} },
      async (_args, extra) => json(await listTags(tenantOf(extra))),
    );

    server.registerTool(
      "get_instructions",
      {
        description:
          "Get the user's standing instructions for how to read, interpret, and organize their voice notes. Call this first so you treat their notes the way they want.",
        inputSchema: {},
      },
      async (_args, extra) => {
        const instructions = await getInstructions(tenantOf(extra));
        return json({
          instructions:
            instructions ||
            "No custom instructions set. Treat notes as the user's personal voice memos.",
        });
      },
    );
  },
  {},
  { basePath: "" },
);

const authHandler = withMcpAuth(
  handler,
  async (_req, bearerToken) => {
    if (!bearerToken) return undefined;
    // OAuth only — Claude connects via the authorization-code/PKCE flow.
    const tenantId = await validateAccessToken(bearerToken, Date.now());
    if (!tenantId) return undefined;
    return { token: bearerToken, scopes: ["mcp"], clientId: tenantId, extra: { tenantId } };
  },
  { required: true },
);

export { authHandler as GET, authHandler as POST };
