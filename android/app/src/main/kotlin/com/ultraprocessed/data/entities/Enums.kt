package com.ultraprocessed.data.entities

/**
 * Where a FoodEntry came from. Affects how confident we are in its values.
 */
enum class FoodSource {
    /** Looked up via Open Food Facts after barcode scan. */
    BARCODE,

    /** OCR'd ingredient text + LLM classification. */
    OCR,

    /** LLM vision analysis on a photograph. */
    LLM,

    /** User-entered manually. */
    MANUAL
}

/**
 * Sync status with the optional backend.
 * Values that haven't synced yet are PENDING; backend confirmation moves
 * them to SYNCED. Permanent failures land in ERROR for inspection.
 */
enum class SyncState {
    PENDING,
    SYNCED,
    ERROR
}

/**
 * Pre-baked fasting schedules.
 *
 * Time-restricted-eating (TRE) variants pin a daily eating window.
 * Multi-day patterns (FIVE_TWO / FOUR_THREE / ADF) instead use a
 * weekday bitmask with a kcal cap on restricted days. CUSTOM lets the
 * user set either kind of pattern by hand.
 */
enum class ScheduleType {
    SIXTEEN_EIGHT,
    EIGHTEEN_SIX,
    TWENTY_FOUR,
    OMAD,
    FIVE_TWO,
    FOUR_THREE,
    ADF,
    CUSTOM
}
