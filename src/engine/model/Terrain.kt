package engine.model

enum class Terrain {
    PLAIN,
    SHALLOW_WATER,
    DEEP_WATER,
    MOUNTAIN,
    VILLAGE,
    CITY,
    FOREST,
    FOG;

    fun isWater(): Boolean = this == SHALLOW_WATER || this == DEEP_WATER
}
