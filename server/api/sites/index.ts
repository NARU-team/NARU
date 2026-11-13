import { SITES, publicSite } from "../../lib/sites.js";

/** GET /api/sites — 시연 지역 목록 */
export function GET(): Response {
  return Response.json(SITES.map(publicSite), {
    headers: { "Cache-Control": "public, max-age=3600" },
  });
}
