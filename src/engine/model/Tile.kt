package engine.model

data class Tile(
    val terrain: Terrain,
    val resource: Resource? = null,
    val unit: Unit? = null,
    val cityId: Int? = null,
    val hasRoad: Boolean = false,
)
