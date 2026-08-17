package engine.model

enum class TribeType(
    val initialTech: Technology?,
    val startingUnit: UnitType,
) {
    XIN_XI(Technology.CLIMBING, UnitType.WARRIOR),
    IMPERIUS(Technology.ORGANIZATION, UnitType.WARRIOR),
    BARDUR(Technology.HUNTING, UnitType.WARRIOR),
    OUMAJI(Technology.RIDING, UnitType.RIDER),
    KICKOO(Technology.FISHING, UnitType.WARRIOR),
    HOODRICK(Technology.ARCHERY, UnitType.ARCHER),
    LUXIDOOR(null, UnitType.WARRIOR),
    VENGIR(Technology.SMITHERY, UnitType.SWORDMAN),
    ZEBASI(Technology.FARMING, UnitType.WARRIOR),
    AI_MO(Technology.MEDITATION, UnitType.WARRIOR),
    QUETZALI(Technology.SHIELDS, UnitType.DEFENDER),
    YADAKK(Technology.ROADS, UnitType.WARRIOR),
}

data class Tribe(
    val id: Int,
    val type: TribeType,
    val stars: Int = INITIAL_STARS,
    val researchedTechs: Set<Technology> = emptySet(),
    val cityIds: List<Int> = emptyList(),
    val score: Int = 0,
    val kills: Int = 0,
) {
    fun hasResearched(tech: Technology): Boolean = tech in researchedTechs

    fun canResearch(tech: Technology): Boolean {
        if (hasResearched(tech)) return false
        val parent = tech.parent ?: return true
        return parent in researchedTechs
    }

    fun getNumCities(): Int = cityIds.size

    fun controlsCity(cityId: Int): Boolean = cityId in cityIds

    companion object {
        const val INITIAL_STARS = 5
    }
}
