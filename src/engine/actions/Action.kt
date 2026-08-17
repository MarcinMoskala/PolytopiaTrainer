package engine.actions

import engine.model.Position
import engine.model.Technology
import engine.model.UnitType
import engine.model.Building

sealed class Action {
    // Unit actions
    data class Move(val unitId: Int, val destination: Position) : Action()
    data class Attack(val unitId: Int, val targetId: Int) : Action()
    data class Capture(val unitId: Int, val cityId: Int) : Action()
    data class Convert(val unitId: Int, val targetId: Int) : Action()
    data class HealOthers(val unitId: Int, val targetId: Int) : Action()
    data class MakeVeteran(val unitId: Int) : Action()
    data class Recover(val unitId: Int) : Action()
    data class Disband(val unitId: Int) : Action()
    data class Upgrade(val unitId: Int) : Action()
    data class Examine(val unitId: Int, val ruinsPosition: Position) : Action()

    // City actions
    data class Spawn(val cityId: Int, val unitType: UnitType, val position: Position) : Action()
    data class LevelUp(val cityId: Int, val choice: LevelUpChoice) : Action()
    data class Build(val cityId: Int, val building: Building, val position: Position) : Action()
    data class BurnForest(val cityId: Int, val position: Position) : Action()
    data class GrowForest(val cityId: Int, val position: Position) : Action()
    data class ClearForest(val cityId: Int, val position: Position) : Action()
    data class Destroy(val cityId: Int, val building: Building, val position: Position) : Action()

    // Tribe actions
    data class ResearchTech(val tribeId: Int, val technology: Technology) : Action()
    data class EndTurn(val tribeId: Int) : Action()
    data class DeclareWar(val tribeId: Int, val targetTribeId: Int) : Action()
    data class SendStars(val tribeId: Int, val targetTribeId: Int, val amount: Int) : Action()
    data class BuildRoad(val tribeId: Int, val position: Position) : Action()
}

enum class LevelUpChoice {
    WORKSHOP,
    EXPLORER,
    CITY_WALL,
    RESOURCES,
    POP_GROWTH,
    BORDER_GROWTH,
    PARK,
    SUPERUNIT,
}
