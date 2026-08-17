package engine.model

enum class Building(
    val cost: Int,
    val bonus: Int,
    val techRequirement: Technology?,
    val terrainRequirements: Set<Terrain>,
    val resourceConstraint: Resource? = null,
    val adjacencyConstraint: Building? = null,
) {
    PORT(10, 2, Technology.SAILING, setOf(Terrain.SHALLOW_WATER)),
    MINE(5, 2, Technology.MINING, setOf(Terrain.MOUNTAIN), resourceConstraint = Resource.ORE),
    FORGE(5, 2, Technology.SMITHERY, setOf(Terrain.PLAIN), adjacencyConstraint = MINE),
    FARM(5, 2, Technology.FARMING, setOf(Terrain.PLAIN), resourceConstraint = Resource.CROPS),
    WINDMILL(5, 1, Technology.CONSTRUCTION, setOf(Terrain.PLAIN), adjacencyConstraint = FARM),
    CUSTOMS_HOUSE(5, 2, Technology.TRADE, setOf(Terrain.PLAIN), adjacencyConstraint = PORT),
    LUMBER_HUT(2, 1, Technology.FORESTRY, setOf(Terrain.FOREST)),
    SAWMILL(5, 1, Technology.MATHEMATICS, setOf(Terrain.PLAIN), adjacencyConstraint = LUMBER_HUT),
    TEMPLE(20, 1, Technology.FREE_SPIRIT, setOf(Terrain.PLAIN)),
    WATER_TEMPLE(20, 1, Technology.AQUATISM, setOf(Terrain.SHALLOW_WATER, Terrain.DEEP_WATER)),
    FOREST_TEMPLE(15, 1, Technology.SPIRITUALISM, setOf(Terrain.FOREST)),
    MOUNTAIN_TEMPLE(20, 1, Technology.MEDITATION, setOf(Terrain.MOUNTAIN)),

    // Monuments (cost 0, built via city level-up)
    ALTAR_OF_PEACE(0, 3, Technology.MEDITATION, setOf(Terrain.SHALLOW_WATER, Terrain.PLAIN)),
    EMPERORS_TOMB(0, 3, Technology.TRADE, setOf(Terrain.SHALLOW_WATER, Terrain.PLAIN)),
    EYE_OF_GOD(0, 3, Technology.NAVIGATION, setOf(Terrain.SHALLOW_WATER, Terrain.PLAIN)),
    GATE_OF_POWER(0, 3, null, setOf(Terrain.SHALLOW_WATER, Terrain.PLAIN)),
    GRAND_BAZAR(0, 3, Technology.ROADS, setOf(Terrain.SHALLOW_WATER, Terrain.PLAIN)),
    PARK_OF_FORTUNE(0, 3, null, setOf(Terrain.SHALLOW_WATER, Terrain.PLAIN)),
    TOWER_OF_WISDOM(0, 3, Technology.PHILOSOPHY, setOf(Terrain.SHALLOW_WATER, Terrain.PLAIN));

    fun isMonument(): Boolean = this in setOf(
        ALTAR_OF_PEACE, EMPERORS_TOMB, EYE_OF_GOD,
        GATE_OF_POWER, GRAND_BAZAR, PARK_OF_FORTUNE, TOWER_OF_WISDOM
    )

    fun isTemple(): Boolean = this in setOf(TEMPLE, WATER_TEMPLE, FOREST_TEMPLE, MOUNTAIN_TEMPLE)

    fun isBase(): Boolean = this == FARM || this == MINE || this == LUMBER_HUT

    fun matchingBuilding(): Building? = when (this) {
        PORT -> CUSTOMS_HOUSE
        FARM -> WINDMILL
        MINE -> FORGE
        LUMBER_HUT -> SAWMILL
        CUSTOMS_HOUSE -> PORT
        WINDMILL -> FARM
        FORGE -> MINE
        SAWMILL -> LUMBER_HUT
        else -> null
    }
}
