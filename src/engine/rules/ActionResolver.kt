package engine.rules

import engine.actions.Action
import engine.actions.LevelUpChoice
import engine.model.*
import engine.model.Unit

object ActionResolver {

    fun resolve(state: GameState, action: Action): GameState = when (action) {
        is Action.Move -> resolveMove(state, action)
        is Action.Attack -> resolveAttack(state, action)
        is Action.Capture -> resolveCapture(state, action)
        is Action.Convert -> resolveConvert(state, action)
        is Action.HealOthers -> resolveHealOthers(state, action)
        is Action.MakeVeteran -> resolveMakeVeteran(state, action)
        is Action.Recover -> resolveRecover(state, action)
        is Action.Disband -> resolveDisband(state, action)
        is Action.Upgrade -> resolveUpgrade(state, action)
        is Action.Examine -> resolveExamine(state, action)
        is Action.Spawn -> resolveSpawn(state, action)
        is Action.LevelUp -> resolveLevelUp(state, action)
        is Action.Build -> resolveBuild(state, action)
        is Action.BurnForest -> resolveBurnForest(state, action)
        is Action.GrowForest -> resolveGrowForest(state, action)
        is Action.ClearForest -> resolveClearForest(state, action)
        is Action.Destroy -> resolveDestroy(state, action)
        is Action.ResearchTech -> resolveResearchTech(state, action)
        is Action.EndTurn -> resolveEndTurn(state, action)
        is Action.DeclareWar -> resolveDeclareWar(state, action)
        is Action.SendStars -> resolveSendStars(state, action)
        is Action.BuildRoad -> resolveBuildRoad(state, action)
    }

    // ── Unit actions ──────────────────────────────────────────────────────────

    private fun resolveMove(state: GameState, action: Action.Move): GameState {
        val board = state.board
        val srcTile = board.tiles.entries.firstOrNull { it.value.unit?.id == action.unitId } ?: return state
        val unit = srcTile.value.unit ?: return state
        val destTile = board.getTile(action.destination) ?: return state

        val movedUnit = unit.copy(position = action.destination, turnStatus = TurnStatus.MOVED)
        var newBoard = board
            .withUnit(srcTile.key, null)
            .withUnit(action.destination, movedUnit)
        return state.withBoard(newBoard)
    }

    private fun resolveAttack(state: GameState, action: Action.Attack): GameState {
        val board = state.board
        val attackerEntry = board.tiles.entries.firstOrNull { it.value.unit?.id == action.unitId } ?: return state
        val targetEntry = board.tiles.entries.firstOrNull { it.value.unit?.id == action.targetId } ?: return state
        val attacker = attackerEntry.value.unit ?: return state
        val target = targetEntry.value.unit ?: return state
        val targetTile = targetEntry.value
        val targetCity = targetTile.cityId?.let { board.getCity(it) }
        val targetTribe = state.getTribe(target.tribeId) ?: return state

        val result = CombatRules.calculate(attacker, target, targetTile, targetCity, targetTribe)

        val updatedAttacker = attacker.withStatus(TurnStatus.ATTACKED)
        var newBoard = board.withUnit(attackerEntry.key, updatedAttacker)

        if (target.currentHp <= result.attackDamage) {
            // Target killed
            val attackerWithKill = updatedAttacker.copy(kills = updatedAttacker.kills + 1)
                .let { if (it.shouldPromoteToVeteran()) it.copy(isVeteran = true) else it }
            newBoard = newBoard
                .withUnit(attackerEntry.key, attackerWithKill)
                .withUnit(targetEntry.key, null)
            // Melee units move to target position if empty
            if (!attacker.type.isRanged()) {
                val finalAttacker = attackerWithKill.copy(position = targetEntry.key)
                newBoard = newBoard
                    .withUnit(attackerEntry.key, null)
                    .withUnit(targetEntry.key, finalAttacker)
            }
        } else {
            // Target survives, take retaliation if in range
            val damagedTarget = target.withHp(target.currentHp - result.attackDamage)
            newBoard = newBoard.withUnit(targetEntry.key, damagedTarget)

            val distance = attackerEntry.key.chebyshevDistance(targetEntry.key)
            if (distance <= target.range) {
                val damagedAttacker = updatedAttacker.withHp(updatedAttacker.currentHp - result.retaliationDamage)
                if (damagedAttacker.currentHp <= 0) {
                    val targetWithKill = damagedTarget.copy(kills = damagedTarget.kills + 1)
                        .let { if (it.shouldPromoteToVeteran()) it.copy(isVeteran = true) else it }
                    newBoard = newBoard
                        .withUnit(attackerEntry.key, null)
                        .withUnit(targetEntry.key, targetWithKill)
                } else {
                    newBoard = newBoard.withUnit(attackerEntry.key, damagedAttacker)
                }
            }
        }

        return state.withBoard(newBoard)
    }

    private fun resolveCapture(state: GameState, action: Action.Capture): GameState {
        val board = state.board
        val unitEntry = board.tiles.entries.firstOrNull { it.value.unit?.id == action.unitId } ?: return state
        val unit = unitEntry.value.unit ?: return state
        val city = board.getCity(action.cityId) ?: return state
        val oldTribeId = city.tribeId

        val capturedCity = city.copy(tribeId = unit.tribeId, hasWalls = false)
        val updatedUnit = unit.withStatus(TurnStatus.FINISHED)

        val oldTribe = state.getTribe(oldTribeId)?.let { it.copy(cityIds = it.cityIds - action.cityId) }
        val newTribe = state.getTribe(unit.tribeId)?.let { it.copy(cityIds = it.cityIds + action.cityId) }

        var newState = state.withBoard(board.withCity(capturedCity).withUnit(unitEntry.key, updatedUnit))
        if (oldTribe != null) newState = newState.withTribe(oldTribe)
        if (newTribe != null) newState = newState.withTribe(newTribe)
        return newState
    }

    private fun resolveConvert(state: GameState, action: Action.Convert): GameState {
        val board = state.board
        val converterEntry = board.tiles.entries.firstOrNull { it.value.unit?.id == action.unitId } ?: return state
        val converter = converterEntry.value.unit ?: return state
        val targetEntry = board.tiles.entries.firstOrNull { it.value.unit?.id == action.targetId } ?: return state
        val target = targetEntry.value.unit ?: return state

        val convertedUnit = target.copy(tribeId = converter.tribeId, turnStatus = TurnStatus.FINISHED)
        val updatedConverter = converter.withStatus(TurnStatus.FINISHED)

        val newBoard = board
            .withUnit(converterEntry.key, updatedConverter)
            .withUnit(targetEntry.key, convertedUnit)
        return state.withBoard(newBoard)
    }

    private fun resolveHealOthers(state: GameState, action: Action.HealOthers): GameState {
        val board = state.board
        val healerEntry = board.tiles.entries.firstOrNull { it.value.unit?.id == action.unitId } ?: return state
        val healer = healerEntry.value.unit ?: return state
        val targetEntry = board.tiles.entries.firstOrNull { it.value.unit?.id == action.targetId } ?: return state
        val target = targetEntry.value.unit ?: return state

        val healedTarget = target.withHp(target.currentHp + Unit.MINDBENDER_HEAL)
        val updatedHealer = healer.withStatus(TurnStatus.FINISHED)

        val newBoard = board
            .withUnit(healerEntry.key, updatedHealer)
            .withUnit(targetEntry.key, healedTarget)
        return state.withBoard(newBoard)
    }

    private fun resolveMakeVeteran(state: GameState, action: Action.MakeVeteran): GameState {
        val board = state.board
        val entry = board.tiles.entries.firstOrNull { it.value.unit?.id == action.unitId } ?: return state
        val unit = entry.value.unit ?: return state
        val veteran = unit.copy(isVeteran = true, turnStatus = TurnStatus.FINISHED)
        return state.withBoard(board.withUnit(entry.key, veteran))
    }

    private fun resolveRecover(state: GameState, action: Action.Recover): GameState {
        val board = state.board
        val entry = board.tiles.entries.firstOrNull { it.value.unit?.id == action.unitId } ?: return state
        val unit = entry.value.unit ?: return state
        val healed = unit.withHp(unit.currentHp + Unit.RECOVER_PLUS_HP + Unit.RECOVER_IN_BORDERS_PLUS_HP)
            .withStatus(TurnStatus.FINISHED)
        return state.withBoard(board.withUnit(entry.key, healed))
    }

    private fun resolveDisband(state: GameState, action: Action.Disband): GameState {
        val board = state.board
        val entry = board.tiles.entries.firstOrNull { it.value.unit?.id == action.unitId } ?: return state
        val unit = entry.value.unit ?: return state
        val tribe = state.getTribe(unit.tribeId) ?: return state
        val refund = unit.type.cost / 2
        val updatedTribe = tribe.copy(stars = tribe.stars + refund)
        val newBoard = board.withUnit(entry.key, null)
        return state.withBoard(newBoard).withTribe(updatedTribe)
    }

    private fun resolveUpgrade(state: GameState, action: Action.Upgrade): GameState {
        val board = state.board
        val entry = board.tiles.entries.firstOrNull { it.value.unit?.id == action.unitId } ?: return state
        val unit = entry.value.unit ?: return state
        val upgradedType = when (unit.type) {
            UnitType.BOAT -> UnitType.SHIP
            UnitType.SHIP -> UnitType.BATTLESHIP
            else -> return state
        }
        val tribe = state.getTribe(unit.tribeId) ?: return state
        val cost = upgradedType.cost
        if (tribe.stars < cost) return state
        val upgraded = unit.copy(type = upgradedType, currentHp = upgradedType.maxHp, turnStatus = TurnStatus.FINISHED)
        val updatedTribe = tribe.copy(stars = tribe.stars - cost)
        return state.withBoard(board.withUnit(entry.key, upgraded)).withTribe(updatedTribe)
    }

    private fun resolveExamine(state: GameState, action: Action.Examine): GameState {
        val board = state.board
        val entry = board.tiles.entries.firstOrNull { it.value.unit?.id == action.unitId } ?: return state
        val unit = entry.value.unit ?: return state
        // Remove ruins resource from tile
        val ruinsTile = board.getTile(action.ruinsPosition) ?: return state
        val clearedTile = ruinsTile.copy(resource = null)
        val updatedUnit = unit.withStatus(TurnStatus.FINISHED)
        val newBoard = board.withTile(action.ruinsPosition, clearedTile).withUnit(entry.key, updatedUnit)
        return state.withBoard(newBoard)
    }

    // ── City actions ──────────────────────────────────────────────────────────

    private fun resolveSpawn(state: GameState, action: Action.Spawn): GameState {
        val board = state.board
        val city = board.getCity(action.cityId) ?: return state
        val tribe = state.getTribe(city.tribeId) ?: return state
        val cost = action.unitType.cost
        if (tribe.stars < cost) return state
        if (board.getTile(action.position)?.unit != null) return state

        val newUnitId = (board.allUnits().maxOfOrNull { it.second.id } ?: 0) + 1
        val newUnit = Unit(
            id = newUnitId,
            type = action.unitType,
            tribeId = tribe.id,
            position = action.position,
            turnStatus = TurnStatus.MOVED,
        )
        val updatedTribe = tribe.copy(stars = tribe.stars - cost)
        val newBoard = board.withUnit(action.position, newUnit)
        return state.withBoard(newBoard).withTribe(updatedTribe)
    }

    private fun resolveLevelUp(state: GameState, action: Action.LevelUp): GameState {
        val board = state.board
        val city = board.getCity(action.cityId) ?: return state
        if (!city.canLevelUp()) return state

        val leveled = when (action.choice) {
            LevelUpChoice.CITY_WALL -> city.leveled().copy(hasWalls = true)
            LevelUpChoice.BORDER_GROWTH -> city.leveled().copy(bound = city.bound + 1)
            else -> city.leveled()
        }
        return state.withBoard(board.withCity(leveled))
    }

    private fun resolveBuild(state: GameState, action: Action.Build): GameState {
        val board = state.board
        val city = board.getCity(action.cityId) ?: return state
        val tribe = state.getTribe(city.tribeId) ?: return state
        val cost = action.building.cost
        if (tribe.stars < cost) return state

        val newBuilding = CityBuilding(type = action.building, position = action.position)
        val updatedCity = city.copy(buildings = city.buildings + newBuilding)
        val updatedTribe = tribe.copy(stars = tribe.stars - cost)
        return state.withBoard(board.withCity(updatedCity)).withTribe(updatedTribe)
    }

    private fun resolveBurnForest(state: GameState, action: Action.BurnForest): GameState {
        val board = state.board
        val city = board.getCity(action.cityId) ?: return state
        val tribe = state.getTribe(city.tribeId) ?: return state
        if (tribe.stars < BURN_FOREST_COST) return state
        val tile = board.getTile(action.position) ?: return state
        if (tile.terrain != Terrain.FOREST) return state

        val updatedTile = tile.copy(terrain = Terrain.PLAIN)
        val updatedTribe = tribe.copy(stars = tribe.stars - BURN_FOREST_COST)
        return state.withBoard(board.withTile(action.position, updatedTile)).withTribe(updatedTribe)
    }

    private fun resolveGrowForest(state: GameState, action: Action.GrowForest): GameState {
        val board = state.board
        val city = board.getCity(action.cityId) ?: return state
        val tribe = state.getTribe(city.tribeId) ?: return state
        if (tribe.stars < GROW_FOREST_COST) return state
        val tile = board.getTile(action.position) ?: return state
        if (tile.terrain != Terrain.PLAIN) return state

        val updatedTile = tile.copy(terrain = Terrain.FOREST)
        val updatedTribe = tribe.copy(stars = tribe.stars - GROW_FOREST_COST)
        return state.withBoard(board.withTile(action.position, updatedTile)).withTribe(updatedTribe)
    }

    private fun resolveClearForest(state: GameState, action: Action.ClearForest): GameState {
        val board = state.board
        val city = board.getCity(action.cityId) ?: return state
        val tribe = state.getTribe(city.tribeId) ?: return state
        val tile = board.getTile(action.position) ?: return state
        if (tile.terrain != Terrain.FOREST) return state

        val updatedTile = tile.copy(terrain = Terrain.PLAIN)
        val updatedTribe = tribe.copy(stars = tribe.stars + CLEAR_FOREST_STAR)
        return state.withBoard(board.withTile(action.position, updatedTile)).withTribe(updatedTribe)
    }

    private fun resolveDestroy(state: GameState, action: Action.Destroy): GameState {
        val board = state.board
        val city = board.getCity(action.cityId) ?: return state
        val updatedCity = city.copy(buildings = city.buildings.filter {
            !(it.type == action.building && it.position == action.position)
        })
        return state.withBoard(board.withCity(updatedCity))
    }

    // ── Tribe actions ─────────────────────────────────────────────────────────

    private fun resolveResearchTech(state: GameState, action: Action.ResearchTech): GameState {
        val tribe = state.getTribe(action.tribeId) ?: return state
        if (!tribe.canResearch(action.technology)) return state
        val cost = action.technology.cost(tribe.getNumCities(), tribe.researchedTechs)
        if (tribe.stars < cost) return state

        val updatedTribe = tribe.copy(
            stars = tribe.stars - cost,
            researchedTechs = tribe.researchedTechs + action.technology,
        )
        return state.withTribe(updatedTribe)
    }

    private fun resolveEndTurn(state: GameState, action: Action.EndTurn): GameState {
        val tribe = state.getTribe(action.tribeId) ?: return state

        // Collect star income from all cities owned by this tribe
        val income = tribe.cityIds.sumOf { cityId ->
            state.board.getCity(cityId)?.starIncome ?: 0
        }
        val updatedTribe = tribe.copy(stars = tribe.stars + income)

        // Reset all units of this tribe to FRESH
        var newBoard = state.board
        for ((pos, unit) in state.board.unitsForTribe(tribe.id)) {
            newBoard = newBoard.withUnit(pos, unit.withStatus(TurnStatus.FRESH))
        }

        val newState = state.withBoard(newBoard).withTribe(updatedTribe)
        val nextIndex = newState.nextTribeIndex()
        val newTurn = if (newState.isLastTribeInTurn()) newState.turn + 1 else newState.turn
        return newState.copy(currentTribeIndex = nextIndex, turn = newTurn)
    }

    private fun resolveDeclareWar(state: GameState, action: Action.DeclareWar): GameState {
        // Diplomatic state is not modelled in the current data model; no-op for now
        return state
    }

    private fun resolveSendStars(state: GameState, action: Action.SendStars): GameState {
        val sender = state.getTribe(action.tribeId) ?: return state
        val receiver = state.getTribe(action.targetTribeId) ?: return state
        if (sender.stars < action.amount) return state

        val updatedSender = sender.copy(stars = sender.stars - action.amount)
        val updatedReceiver = receiver.copy(stars = receiver.stars + action.amount)
        return state.withTribe(updatedSender).withTribe(updatedReceiver)
    }

    private fun resolveBuildRoad(state: GameState, action: Action.BuildRoad): GameState {
        val tribe = state.getTribe(state.currentTribe.id) ?: return state
        if (tribe.stars < ROAD_COST) return state
        val tile = state.board.getTile(action.position) ?: return state
        val updatedTile = tile.copy(hasRoad = true)
        val updatedTribe = tribe.copy(stars = tribe.stars - ROAD_COST)
        return state.withBoard(state.board.withTile(action.position, updatedTile)).withTribe(updatedTribe)
    }

    // ── Constants ─────────────────────────────────────────────────────────────
    private const val BURN_FOREST_COST = 5
    private const val GROW_FOREST_COST = 5
    private const val CLEAR_FOREST_STAR = 2
    private const val ROAD_COST = 2
}
