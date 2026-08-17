package engine.model

data class Position(val x: Int, val y: Int) {
    fun neighbors(): List<Position> = listOf(
        Position(x - 1, y - 1),
        Position(x,     y - 1),
        Position(x + 1, y - 1),
        Position(x - 1, y),
        Position(x + 1, y),
        Position(x - 1, y + 1),
        Position(x,     y + 1),
        Position(x + 1, y + 1),
    )

    fun chebyshevDistance(other: Position): Int =
        maxOf(Math.abs(x - other.x), Math.abs(y - other.y))
}
