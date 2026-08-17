package engine.rules

import engine.actions.Action
import engine.actions.LevelUpChoice
import engine.model.*
import engine.model.Unit

object AvailableActions {

    fun forUnit(state: GameState, pos: Position): List<Action> {
        val unit = state.board.getUnit(pos) ?: return emptyList()
        if (unit.tribeId != state.currentTribe.id) return emptyList()
        val tribe = state.getTribe(unit.tribeId) ?: return emptyList()
        val actions = mutableListOf<Action>()

        // Move
        if (unit.canMove()) {
            reachablePositions(state, pos, unit).forEach { dest ->
                if (state.board.getUnit(dest) == null) {
                    actions += Action.Move(unit.id, dest)
                }
            }
        }

        // Attack / Capture / Convert
        if (unit.canAttack() && unit.type != UnitType.MIND_BENDER) {
            val attackRange = unit.range
            state.board.positionsInRange(pos, attackRange).forEach { targetPos ->
                val targetUnit = state.board.getUnit(targetPos) ?: return@forEach
                if (targetUnit.tribeId != unit.tribeId) {
                    val targetTile = state.board.getTile(targetPos) ?: return@forEach
                    if (targetTile.terrain == Terrain.CITY) {
                        val city = state.board.getCityAt(targetPos)
                        if (city != null && city.tribeId != unit.tribeId) {
                            actions += Action.Capture(unit.id, city.id)
                        }
                    } else {
                        actions += Action.Attack(unit.id, targetUnit.id)
                    }
                }
            }
            // Capture undefended enemy city
            state.board.neighbors(pos).forEach { neighborPos ->
                val tile = state.board.getTile(neighborPos) ?: return@forEach
                if (tile.terrain == Terrain.CITY && tile.unit == null) {
                    val city = state.board.getCityAt(neighborPos)
                    if (city != null && city.tribeId != unit.tribeId) {
                        actions += Action.Capture(unit.id, city.id)
                    }
                }
            }
        }

        // MindBender: Convert and HealOthers
        if (unit.type == UnitType.MIND_BENDER && unit.canAttack()) {
            state.board.neighbors(pos).forEach { neighborPos ->
                val targetUnit = state.board.getUnit(neighborPos) ?: return@forEach
                if (targetUnit.tribeId != unit.tribeId) {
                    actions += Action.Convert(unit.id, targetUnit.id)
                } else if (targetUnit.currentHp < targetUnit.maxHp) {
                    actions += Action.HealOthers(unit.id, targetUnit.id)
                }
            }
        }

        // Recover (heal self, only when FRESH and not at full HP)
        if (unit.turnStatus == TurnStatus.FRESH && unit.currentHp < unit.maxHp) {
            actions += Action.Recover(unit.id)
        }

        // MakeVeteran (when enough kills and not yet veteran)
        if (unit.shouldPromoteToVeteran()) {
            actions += Action.MakeVeteran(unit.id)
        }

        // Disband (requires FREE_SPIRIT)
        if (tribe.hasResearched(Technology.FREE_SPIRIT)) {
            actions += Action.Disband(unit.id)
        }

        // Upgrade boat/ship
        if (unit.type == UnitType.BOAT && tribe.hasResearched(Technology.SAILING)) {
            actions += Action.Upgrade(unit.id)
        }
        if (unit.type == UnitType.SHIP && tribe.hasResearched(Technology.NAVIGATION)) {
            actions += Action.Upgrade(unit.id)
        }

        // Examine ruins
        if (unit.turnStatus == TurnStatus.FRESH) {
            state.board.neighbors(pos).forEach { neighborPos ->
                val tile = state.board.getTile(neighborPos) ?: return@forEach
                if (tile.resource == Resource.RUINS) {
                    actions += Action.Examine(unit.id, neighborPos)
                }
            }
        }

        return actions
    }

    fun forCity(state: GameState, cityId: Int): List<Action> {
        val city = state.board.getCity(cityId) ?: return emptyList()
        if (city.tribeId != state.currentTribe.id) return emptyList()
        val tribe = state.getTribe(city.tribeId) ?: return emptyList()
        val actions = mutableListOf<Action>()

        // LevelUp
        if (city.canLevelUp()) {
            LevelUpChoice.entries
                .filter { it.validForLevel(city.level) }
                .forEach { choice -> actions += Action.LevelUp(cityId, choice) }
        }

        // Spawn units
        UnitType.entries.filter { it.isSpawnable() }.forEach { unitType ->
            val techReq = unitType.techRequirement
            if (techReq == null || tribe.hasResearched(techReq)) {
                if (tribe.stars >= unitType.cost) {
                    // Find adjacent empty land tiles
                    state.board.neighbors(city.position)
                        .filter { pos ->
                            val tile = state.board.getTile(pos) ?: return@filter false
                            tile.unit == null && !tile.terrain.isWater()
                        }
                        .forEach { pos -> actions += Action.Spawn(cityId, unitType, pos) }
                }
            }
        }

        // Build buildings
        Building.entries.filter { !it.isMonument() }.forEach { building ->
            val techReq = building.techRequirement
            if (techReq == null || tribe.hasResearched(techReq)) {
                if (tribe.stars >= building.cost && !city.hasBuilding(building)) {
                    // Find valid tiles in city territory
                    state.board.positionsInRange(city.position, city.bound).forEach { pos ->
                        val tile = state.board.getTile(pos) ?: return@forEach
                        if (building.terrainRequirements.contains(tile.terrain)) {
                            val resConstraint = building.resourceConstraint
                            if (resConstraint == null || tile.resource == resConstraint) {
                                val adjConstraint = building.adjacencyConstraint
                                if (adjConstraint == null || hasAdjacentBuilding(state, pos, adjConstraint)) {
                                    if (city.getBuildingAt(pos) == null) {
                                        actions += Action.Build(cityId, building, pos)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Forest actions within city territory
        state.board.positionsInRange(city.position, city.bound).forEach { pos ->
            val tile = state.board.getTile(pos) ?: return@forEach
            if (tile.terrain == Terrain.FOREST) {
                actions += Action.ClearForest(cityId, pos)
                if (tribe.hasResearched(Technology.CHIVALRY) && tribe.stars >= 5) {
                    actions += Action.BurnForest(cityId, pos)
                }
            }
            if (tile.terrain == Terrain.PLAIN && tribe.hasResearched(Technology.SPIRITUALISM) && tribe.stars >= 5) {
                actions += Action.GrowForest(cityId, pos)
            }
        }

        // Destroy buildings
        city.buildings.forEach { cityBuilding ->
            if (tribe.hasResearched(Technology.CONSTRUCTION)) {
                actions += Action.Destroy(cityId, cityBuilding.type, cityBuilding.position)
            }
        }

        return actions
    }

    fun forTribe(state: GameState): List<Action> {
        val tribe = state.currentTribe
        val actions = mutableListOf<Action>()

        // EndTurn always available
        actions += Action.EndTurn(tribe.id)

        // ResearchTech
        Technology.entries.filter { tribe.canResearch(it) }.forEach { tech ->
            val cost = tech.cost(tribe.getNumCities(), tribe.researchedTechs)
            if (tribe.stars >= cost) {
                actions += Action.ResearchTech(tribe.id, tech)
            }
        }

        // DeclareWar against tribes met (simplified: all other tribes)
        state.tribes.filter { it.id != tribe.id }.forEach { other ->
            actions += Action.DeclareWar(tribe.id, other.id)
        }

        // SendStars
        if (tribe.stars >= MIN_STARS_SEND) {
            state.tribes.filter { it.id != tribe.id }.forEach { other ->
                actions += Action.SendStars(tribe.id, other.id, MIN_STARS_SEND)
            }
        }

        // BuildRoad
        if (tribe.hasResearched(Technology.ROADS) && tribe.stars >= ROAD_COST) {
            state.board.tiles.entries.forEach { (pos, tile) ->
                if (!tile.hasRoad && !tile.terrain.isWater() && tile.terrain != Terrain.MOUNTAIN) {
                    // Only on tiles within any owned city territory
                    if (tribe.cityIds.any { cityId ->
                            val city = state.board.getCity(cityId) ?: return@any false
                            city.position.chebyshevDistance(pos) <= city.bound
                        }) {
                        actions += Action.BuildRoad(tribe.id, pos)
                    }
                }
            }
        }

        return actions
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun reachablePositions(state: GameState, from: Position, unit: Unit): Set<Position> {
        val isWater = unit.type.isWaterUnit()
        val visited = mutableSetOf<Position>()
        val frontier = mutableListOf(from to unit.movement)
        while (frontier.isNotEmpty()) {
            val (pos, remaining) = frontier.removeFirst()
            if (!visited.add(pos)) continue
            if (remaining <= 0) continue
            state.board.neighbors(pos).forEach { neighbor ->
                if (state.board.isPassable(neighbor, isWater) && neighbor !in visited) {
                    frontier += neighbor to remaining - 1
                }
            }
        }
        visited -= from
        return visited
    }

    private fun hasAdjacentBuilding(state: GameState, pos: Position, buildingType: Building): Boolean =
        state.board.neighbors(pos).any { neighborPos ->
            state.board.tiles[neighborPos]?.let { tile ->
                state.board.cities.values.any { city ->
                    city.getBuildingAt(neighborPos)?.type == buildingType
                }
            } ?: false
        }

    private fun LevelUpChoice.validForLevel(cityLevel: Int): Boolean = when (cityLevel) {
        1 -> this == LevelUpChoice.WORKSHOP || this == LevelUpChoice.EXPLORER
        2 -> this == LevelUpChoice.CITY_WALL || this == LevelUpChoice.RESOURCES
        3 -> this == LevelUpChoice.POP_GROWTH || this == LevelUpChoice.BORDER_GROWTH
        else -> this == LevelUpChoice.PARK || this == LevelUpChoice.SUPERUNIT
    }

    private const val MIN_STARS_SEND = 15
    private const val ROAD_COST = 2
}
