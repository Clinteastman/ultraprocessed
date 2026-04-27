import type {
  AggregateResponse,
  ConsumptionLogDto,
  FoodEntryDto,
  HaSnapshot
} from "./types";

const STORAGE_KEY_BASE = "ultraprocessed.baseUrl";
const STORAGE_KEY_TOKEN = "ultraprocessed.token";

export function getBaseUrl(): string {
  if (typeof localStorage === "undefined") return "";
  return localStorage.getItem(STORAGE_KEY_BASE) ?? "";
}

export function setBaseUrl(value: string): void {
  if (typeof localStorage === "undefined") return;
  localStorage.setItem(STORAGE_KEY_BASE, value);
}

export function getToken(): string {
  if (typeof localStorage === "undefined") return "";
  return localStorage.getItem(STORAGE_KEY_TOKEN) ?? "";
}

export function setToken(value: string): void {
  if (typeof localStorage === "undefined") return;
  localStorage.setItem(STORAGE_KEY_TOKEN, value);
}

function buildUrl(path: string): string {
  const base = getBaseUrl();
  // Same-origin in production (backend serves the dashboard); explicit
  // base URL when running `vite dev` against a remote backend.
  if (!base) return path;
  return `${base.replace(/\/$/, "")}${path}`;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getToken();
  const headers = new Headers(init?.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  if (init?.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const response = await fetch(buildUrl(path), { ...init, headers });
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
  consumption: (from?: string, to?: string) => {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const qs = params.toString();
    return request<ConsumptionLogDto[]>(`/api/v1/consumption${qs ? `?${qs}` : ""}`);
  }
};

export { STORAGE_KEY_BASE, STORAGE_KEY_TOKEN };
