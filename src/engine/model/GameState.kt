package engine.model

data class GameState(
    val board: Board,
    val tribes: List<Tribe>,
    val currentTribeIndex: Int = 0,
    val turn: Int = 1,
) {
    val currentTribe: Tribe get() = tribes[currentTribeIndex]

    fun getTribe(id: Int): Tribe? = tribes.firstOrNull { it.id == id }

    fun withBoard(board: Board): GameState = copy(board = board)

    fun withTribe(tribe: Tribe): GameState =
        copy(tribes = tribes.map { if (it.id == tribe.id) tribe else it })

    fun withCurrentTribe(tribe: Tribe): GameState =
        withTribe(tribe)

    fun nextTribeIndex(): Int = (currentTribeIndex + 1) % tribes.size

    fun isLastTribeInTurn(): Boolean = currentTribeIndex == tribes.size - 1
}
