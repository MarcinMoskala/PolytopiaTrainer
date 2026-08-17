package engine.model

data class CityBuilding(
    val type: Building,
    val position: Position,
)

data class City(
    val id: Int,
    val position: Position,
    val tribeId: Int,
    val level: Int = 1,
    val population: Int = 0,
    val populationNeed: Int = 2,
    val production: Int = 0,
    val isCapital: Boolean = false,
    val hasWalls: Boolean = false,
    val bound: Int = 1,
    val unitIds: List<Int> = emptyList(),
    val buildings: List<CityBuilding> = emptyList(),
) {
    fun canLevelUp(): Boolean = population >= populationNeed

    fun leveled(): City = copy(
        level = level + 1,
        population = population - populationNeed,
        populationNeed = level + 2,
    )

    val starIncome: Int
        get() = if (population >= 0) level + production + (if (isCapital) PROD_CAPITAL_BONUS else 0)
                else population

    fun canAddUnit(): Boolean = unitIds.size < (level + 1)

    fun hasBuilding(type: Building): Boolean = buildings.any { it.type == type }

    fun getBuildingAt(pos: Position): CityBuilding? = buildings.firstOrNull { it.position == pos }

    companion object {
        const val PROD_CAPITAL_BONUS = 1
    }
}
