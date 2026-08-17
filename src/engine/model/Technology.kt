package engine.model

enum class Technology(val tier: Int, val parent: Technology?) {
    // Tier 1
    CLIMBING(1, null),
    FISHING(1, null),
    HUNTING(1, null),
    ORGANIZATION(1, null),
    RIDING(1, null),

    // Tier 2
    ARCHERY(2, HUNTING),
    FARMING(2, ORGANIZATION),
    FORESTRY(2, HUNTING),
    FREE_SPIRIT(2, RIDING),
    MEDITATION(2, CLIMBING),
    MINING(2, CLIMBING),
    ROADS(2, RIDING),
    SAILING(2, FISHING),
    SHIELDS(2, ORGANIZATION),
    WHALING(2, FISHING),

    // Tier 3
    AQUATISM(3, WHALING),
    CHIVALRY(3, FREE_SPIRIT),
    CONSTRUCTION(3, FARMING),
    MATHEMATICS(3, FORESTRY),
    NAVIGATION(3, SAILING),
    SMITHERY(3, MINING),
    SPIRITUALISM(3, ARCHERY),
    TRADE(3, ROADS),
    PHILOSOPHY(3, MEDITATION);

    fun children(): List<Technology> = entries.filter { it.parent == this }

    fun cost(numCities: Int, researched: Set<Technology>): Int {
        val base = TECH_BASE_COST + tier * numCities
        return if (PHILOSOPHY in researched) (base * TECH_DISCOUNT_VALUE).toInt() else base
    }

    fun points(): Int = tier * TECH_TIER_POINTS

    companion object {
        const val TECH_BASE_COST = 4
        const val TECH_DISCOUNT_VALUE = 0.2
        const val TECH_TIER_POINTS = 100
    }
}
