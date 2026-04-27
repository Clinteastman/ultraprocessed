// Types match backend/app/api/v1/*.py wire DTOs.

export type FoodSource = "BARCODE" | "OCR" | "LLM" | "MANUAL";

export interface FoodEntryDto {
  client_uuid: string;
  name: string;
  brand: string | null;
  barcode: string | null;
  nova_class: number;
  nova_rationale: string;
  kcal_per_100g: number | null;
  kcal_per_unit: number | null;
  serving_description: string | null;
  image_url: string | null;
  ingredients_json: string;
  nutrients_json: string | null;
  source: FoodSource;
  confidence: number;
  created_at: string;
  updated_at: string;
}

export interface ConsumptionLogDto {
  client_uuid: string;
  food_client_uuid: string;
  percentage_eaten: number;
  eaten_at: string;
  lat: number | null;
  lng: number | null;
  location_label: string | null;
  kcal_consumed_snapshot: number | null;
  nutrients_consumed_json: string | null;
  created_at: string;
}

export interface NovaBucket {
  meals: number;
  calories: number;
}

export interface NutrientAdequacyDto {
  consumed: number;
  reference: number;
  pct: number;
  direction: "low" | "ok" | "high";
}

export interface AggregateResponse {
  from: string;
  to: string;
  meal_count: number;
  calories_consumed: number;
  calorie_reference: number;
  nova_breakdown: Record<string, NovaBucket>;
  nova_average: number | null;
  nutrients_consumed: Record<string, number>;
  nutrients_adequacy: Record<string, NutrientAdequacyDto>;
}

export interface LastMealDto {
  name: string;
  nova_class: number;
  eaten_at: string;
  kcal: number | null;
  percentage_eaten: number;
}

export interface HaSnapshot {
  calories_today: number;
  nova_average_today: number | null;
  nova4_calories_today: number;
  meals_today: number;
  last_meal: LastMealDto | null;
  currently_fasting: boolean;
  next_eat_at: string | null;
  nutrients_today: Record<string, number>;
  nutrients_reference: Record<string, number>;
}
