package engine.model

data class Board(
    val tiles: Map<Position, Tile>,
    val cities: Map<Int, City> = emptyMap(),
    val size: Int = 11,
) {
    fun getTile(pos: Position): Tile? = tiles[pos]

    fun getTile(x: Int, y: Int): Tile? = tiles[Position(x, y)]

    fun getUnit(pos: Position): Unit? = tiles[pos]?.unit

    fun getCity(id: Int): City? = cities[id]

    fun getCityAt(pos: Position): City? = tiles[pos]?.cityId?.let { cities[it] }

    fun isPassable(pos: Position, isWaterUnit: Boolean): Boolean {
        val tile = tiles[pos] ?: return false
        return if (isWaterUnit) tile.terrain.isWater()
        else !tile.terrain.isWater() && tile.terrain != Terrain.DEEP_WATER
    }

    fun neighbors(pos: Position): List<Position> =
        pos.neighbors().filter { it in tiles }

    fun positionsInRange(center: Position, range: Int): List<Position> =
        tiles.keys.filter { center.chebyshevDistance(it) <= range }

    fun withTile(pos: Position, tile: Tile): Board = copy(tiles = tiles + (pos to tile))

    fun withCity(city: City): Board = copy(cities = cities + (city.id to city))

    fun withUnit(pos: Position, unit: Unit?): Board {
        val tile = tiles[pos] ?: return this
        return withTile(pos, tile.copy(unit = unit))
    }

    fun allUnits(): List<Pair<Position, Unit>> =
        tiles.entries.mapNotNull { (pos, tile) -> tile.unit?.let { pos to it } }

    fun unitsForTribe(tribeId: Int): List<Pair<Position, Unit>> =
        allUnits().filter { (_, u) -> u.tribeId == tribeId }
}
