package engine

class GameState

enum class Action {
    BuildFarm,
    SpawnWarrior,
    // ...
}

class UserAction(
    val action: Action,
    val position: Position,
)

data class Position(val x: Int, val y: Int)

enum class Tile {
    Map, 
    Unit,
}
