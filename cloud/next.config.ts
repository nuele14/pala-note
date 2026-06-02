import type { NextConfig } from "next";
import path from "path";

const nextConfig: NextConfig = {
  // Pin the workspace root so Turbopack doesn't pick up an unrelated parent lockfile.
  turbopack: {
    root: path.join(__dirname),
  },
};

export default nextConfig;
