package engine

import engine.actions.Action
import engine.model.GameState as ModelGameState
import engine.model.Position as ModelPosition
import engine.rules.AvailableActions

// Legacy aliases kept for compatibility with existing test skeleton
typealias GameState = ModelGameState
typealias Position = ModelPosition

typealias UserAction = Action

enum class TileKind {
    Map,
    Unit,
}

fun ModelGameState.getUserActions(tile: TileKind, position: ModelPosition): List<UserAction> =
    when (tile) {
        TileKind.Unit -> {
            val unit = board.getUnit(position)
            if (unit != null) AvailableActions.forUnit(this, position)
            else emptyList()
        }
        TileKind.Map -> {
            val cityId = board.getTile(position)?.cityId
            if (cityId != null) AvailableActions.forCity(this, cityId)
            else emptyList()
        }
    }

fun ModelGameState.getUserActions(): List<UserAction> {
    val unitActions = board.unitsForTribe(currentTribe.id)
        .flatMap { (pos, _) -> AvailableActions.forUnit(this, pos) }
    val cityActions = currentTribe.cityIds
        .flatMap { cityId -> AvailableActions.forCity(this, cityId) }
    val tribeActions = AvailableActions.forTribe(this)
    return unitActions + cityActions + tribeActions
}
