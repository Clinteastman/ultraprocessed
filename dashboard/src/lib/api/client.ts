import type {
  AggregateResponse,
  ConsumptionLogDto,
  FoodEntryDto,
  HaSnapshot
} from "./types";

const STORAGE_KEY_TOKEN = "ultraprocessed.token";

/**
 * The dashboard is always served by the backend container, so all API
 * calls are same-origin. Vite's dev server proxies /api/* to the local
 * backend (see vite.config.ts), so `pnpm dev` works the same way.
 */

export function getToken(): string {
  if (typeof localStorage === "undefined") return "";
  return localStorage.getItem(STORAGE_KEY_TOKEN) ?? "";
}

export function setToken(value: string): void {
  if (typeof localStorage === "undefined") return;
  localStorage.setItem(STORAGE_KEY_TOKEN, value);
}

export function clearToken(): void {
  if (typeof localStorage === "undefined") return;
  localStorage.removeItem(STORAGE_KEY_TOKEN);
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getToken();
  const headers = new Headers(init?.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init?.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const response = await fetch(path, { ...init, headers });
  if (!response.ok) {
    const body = await response.text().catch(() => "");
    throw new ApiError(response.status, body || response.statusText);
  }
  return (await response.json()) as T;
}

export class ApiError extends Error {
  constructor(public status: number, public body: string) {
    super(`HTTP ${status}: ${body.slice(0, 200)}`);
  }
}

export interface TargetsDto {
  calorie_target_kcal: number;
}

export const api = {
  health: () => request<{ status: string; version: string }>("/api/v1/health"),
  whoami: () => request<{ device_id: number; user_id: number; device_name: string }>("/api/v1/auth/whoami"),
  pair: (deviceName: string) =>
    request<{ device_id: number; user_id: number; token: string }>("/api/v1/auth/token", {
      method: "POST",
      body: JSON.stringify({ device_name: deviceName })
    }),
  today: () => request<AggregateResponse>("/api/v1/dashboard/today"),
  range: (from: string, to: string) =>
    request<AggregateResponse>(`/api/v1/dashboard/range?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  haSnapshot: () => request<HaSnapshot>("/api/v1/ha/snapshot"),
  recentFoods: (limit = 50) => request<FoodEntryDto[]>(`/api/v1/foods?limit=${limit}`),
  consumption: (from?: string, to?: string, limit = 1000) => {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    params.set("limit", String(limit));
    return request<ConsumptionLogDto[]>(`/api/v1/consumption?${params}`);
  },
  deleteConsumption: (clientUuid: string) =>
    fetch(`/api/v1/consumption/${encodeURIComponent(clientUuid)}`, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${getToken()}` }
    }).then((r) => {
      if (!r.ok) throw new ApiError(r.status, "");
    }),
  deleteFood: (clientUuid: string) =>
    fetch(`/api/v1/foods/${encodeURIComponent(clientUuid)}`, {
      method: "DELETE",
      headers: { Authorization: `Bearer ${getToken()}` }
    }).then((r) => {
      if (!r.ok) throw new ApiError(r.status, "");
    }),
  getTargets: () => request<TargetsDto>("/api/v1/targets"),
  putTargets: (t: TargetsDto) =>
    request<TargetsDto>("/api/v1/targets", {
      method: "PUT",
      body: JSON.stringify(t)
    }),
  getFastingProfile: () =>
    request<FastingProfileDto | null>("/api/v1/fasting/profile"),
  putFastingProfile: (p: FastingProfileDto) =>
    request<FastingProfileDto>("/api/v1/fasting/profile", {
      method: "PUT",
      body: JSON.stringify(p)
    }),
  getFood: (clientUuid: string) =>
    request<FoodEntryDto>(`/api/v1/foods/by-uuid/${encodeURIComponent(clientUuid)}`)
};

export interface FastingProfileDto {
  id?: number | null;
  name: string;
  schedule_type:
    | "SIXTEEN_EIGHT"
    | "EIGHTEEN_SIX"
    | "TWENTY_FOUR"
    | "OMAD"
    | "FIVE_TWO"
    | "FOUR_THREE"
    | "ADF"
    | "CUSTOM";
  eating_window_start_minutes: number;
  eating_window_end_minutes: number;
  restricted_days_mask: number;
  restricted_kcal_target?: number | null;
  active: boolean;
}

/**
 * Idempotent first-load auth: if no token is stored, request a fresh one.
 * Single-user mode means this just creates a new device row each time;
 * multi-user mode (later) will gate this behind a real sign-in.
 */
export async function ensurePaired(deviceName = "browser"): Promise<void> {
  if (getToken()) return;
  const result = await api.pair(deviceName);
  setToken(result.token);
}

export { STORAGE_KEY_TOKEN };
