// Types match backend/app/api/v1/*.py wire DTOs.

export type FoodSource = "BARCODE" | "OCR" | "LLM" | "MANUAL";

export type FodmapLevel = "UNKNOWN" | "LOW" | "MODERATE" | "HIGH";

export interface FoodEntryDto {
  client_uuid: string;
  name: string;
  brand: string | null;
  barcode: string | null;
  nova_class: number;
  nova_rationale: string;
  kcal_per_100g: number | null;
  kcal_per_unit: number | null;
  grams_per_unit: number | null;
  package_grams: number | null;
  serving_description: string | null;
  image_url: string | null;
  ingredients_json: string;
  nutrients_json: string | null;
  fodmap_level: FodmapLevel;
  fodmap_tags_json: string;
  fodmap_notes: string | null;
  source: FoodSource;
  confidence: number;
  created_at: string;
  updated_at: string;
}

export interface ConsumptionLogDto {
  client_uuid: string;
  food_client_uuid: string;
  percentage_eaten: number;
  grams_eaten: number | null;
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

  // IBS / gut state
  bowel_count_today: number;
  last_bowel_at: string | null;
  minutes_since_last_bowel: number | null;
  last_bristol: number | null;
  bristol_average_today: number | null;
  bowel_flares_today: number;
  symptom_count_today: number;
  symptom_severity_max_today: number;
  last_symptom_kind: string | null;
  last_symptom_at: string | null;
  minutes_since_last_symptom: number | null;
  bloating_today: number;
  heartburn_today: number;
  gut_score_today: number | null;
  period_today: boolean;
}

// ---- IBS DTOs ----

export type SymptomKind =
  | "BLOATING"
  | "HEARTBURN"
  | "CRAMPING"
  | "GAS"
  | "NAUSEA"
  | "URGENCY"
  | "FATIGUE"
  | "PAIN"
  | "OTHER";

export interface BowelEventDto {
  client_uuid: string;
  occurred_at: string;
  bristol: number;       // 1..7
  urgency: number;       // 0..3
  completeness: number;  // 0..2
  pain: number;          // 0..3
  blood: boolean;
  mucus: boolean;
  notes: string | null;
  created_at: string;
}

export interface SymptomEventDto {
  client_uuid: string;
  occurred_at: string;
  kind: SymptomKind;
  severity: number;        // 0..3
  duration_minutes: number | null;
  location: string | null;
  notes: string | null;
  created_at: string;
}

export interface DailyCheckinDto {
  client_uuid: string;
  day: string;  // 'YYYY-MM-DD'
  bloating_overall: number;
  heartburn_overall: number;
  gut_score: number | null;
  mood: number | null;
  stress: number | null;
  period: boolean;
  notes: string | null;
  created_at: string;
  updated_at: string;
}

export interface TriggerScoreDto {
  food_client_uuid: string;
  food_name: string;
  food_brand: string | null;
  fodmap_level: FodmapLevel;
  fodmap_tags: string[];
  nova_class: number;
  score: number;
  flare_count: number;
  baseline_count: number;
  last_flare_at: string | null;
  sample_flares: string[];
}

export interface TriggerReportDto {
  from_dt: string;
  to_dt: string;
  lookback_hours: number;
  half_life_hours: number;
  flare_severity_threshold: number;
  flares_considered: number;
  foods_scored: number;
  foods: TriggerScoreDto[];
}
