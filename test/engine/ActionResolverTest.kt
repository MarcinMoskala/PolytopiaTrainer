package engine

import engine.actions.Action
import engine.actions.LevelUpChoice
import engine.model.*
import engine.model.Unit
import engine.rules.ActionResolver
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ActionResolverTest {

    private fun makeState(
        tiles: Map<Position, Tile>,
        cities: Map<Int, City> = emptyMap(),
        tribes: List<Tribe>,
        currentTribeIndex: Int = 0,
    ): GameState = GameState(
        board = Board(tiles = tiles, cities = cities),
        tribes = tribes,
        currentTribeIndex = currentTribeIndex,
    )

    private fun warrior(id: Int, tribeId: Int, pos: Position, status: TurnStatus = TurnStatus.FRESH) =
        Unit(id = id, type = UnitType.WARRIOR, tribeId = tribeId, position = pos, turnStatus = status)

    // ── Move ─────────────────────────────────────────────────────────────────

    @Test
    fun `Move relocates unit to destination`() {
        val src = Position(0, 0)
        val dst = Position(1, 0)
        val unit = warrior(1, 0, src)
        val state = makeState(
            tiles = mapOf(src to Tile(Terrain.PLAIN, unit = unit), dst to Tile(Terrain.PLAIN)),
            tribes = listOf(Tribe(0, TribeType.IMPERIUS)),
        )
        val result = ActionResolver.resolve(state, Action.Move(unitId = 1, destination = dst))
        assertNull(result.board.getUnit(src))
        assertNotNull(result.board.getUnit(dst))
        assertEquals(dst, result.board.getUnit(dst)?.position)
        assertEquals(TurnStatus.MOVED, result.board.getUnit(dst)?.turnStatus)
    }

    @Test
    fun `Move to non-existent tile does nothing`() {
        val src = Position(0, 0)
        val unit = warrior(1, 0, src)
        val state = makeState(
            tiles = mapOf(src to Tile(Terrain.PLAIN, unit = unit)),
            tribes = listOf(Tribe(0, TribeType.IMPERIUS)),
        )
        val result = ActionResolver.resolve(state, Action.Move(unitId = 1, destination = Position(5, 5)))
        assertEquals(state, result)
    }

    // ── Attack ────────────────────────────────────────────────────────────────

    @Test
    fun `Attack kills weak target and attacker moves to target position`() {
        val attackerPos = Position(0, 0)
        val targetPos = Position(1, 0)
        val attacker = warrior(1, 0, attackerPos)
        val target = Unit(id = 2, type = UnitType.WARRIOR, tribeId = 1, position = targetPos, currentHp = 1)
        val state = makeState(
            tiles = mapOf(
                attackerPos to Tile(Terrain.PLAIN, unit = attacker),
                targetPos to Tile(Terrain.PLAIN, unit = target),
            ),
            tribes = listOf(Tribe(0, TribeType.IMPERIUS), Tribe(1, TribeType.BARDUR)),
        )
        val result = ActionResolver.resolve(state, Action.Attack(unitId = 1, targetId = 2))
        assertNull(result.board.getUnit(attackerPos))
        assertNotNull(result.board.getUnit(targetPos))
        assertEquals(0, result.board.getUnit(targetPos)?.tribeId)  // attacker tribe
        assertEquals(TurnStatus.ATTACKED, result.board.getUnit(targetPos)?.turnStatus)
    }

    @Test
    fun `Attack damages target without killing`() {
        val attackerPos = Position(0, 0)
        val targetPos = Position(1, 0)
        val attacker = warrior(1, 0, attackerPos)
        val target = warrior(2, 1, targetPos)
        val state = makeState(
            tiles = mapOf(
                attackerPos to Tile(Terrain.PLAIN, unit = attacker),
                targetPos to Tile(Terrain.PLAIN, unit = target),
            ),
            tribes = listOf(Tribe(0, TribeType.IMPERIUS), Tribe(1, TribeType.BARDUR)),
        )
        val result = ActionResolver.resolve(state, Action.Attack(unitId = 1, targetId = 2))
        val resultTarget = result.board.getUnit(targetPos)
        assertNotNull(resultTarget)
        assertTrue((resultTarget?.currentHp ?: 10) < 10)
    }

    // ── Capture ───────────────────────────────────────────────────────────────

    @Test
    fun `Capture transfers city to attacker tribe`() {
        val unitPos = Position(0, 0)
        val cityPos = Position(1, 0)
        val city = City(id = 10, position = cityPos, tribeId = 1)
        val unit = warrior(1, 0, unitPos)
        val state = makeState(
            tiles = mapOf(
                unitPos to Tile(Terrain.PLAIN, unit = unit),
                cityPos to Tile(Terrain.CITY, cityId = 10),
            ),
            cities = mapOf(10 to city),
            tribes = listOf(
                Tribe(0, TribeType.IMPERIUS, cityIds = emptyList()),
                Tribe(1, TribeType.BARDUR, cityIds = listOf(10)),
            ),
        )
        val result = ActionResolver.resolve(state, Action.Capture(unitId = 1, cityId = 10))
        assertEquals(0, result.board.getCity(10)?.tribeId)
        assertTrue(result.getTribe(0)?.cityIds?.contains(10) == true)
        assertFalse(result.getTribe(1)?.cityIds?.contains(10) == true)
    }

    // ── Recover ───────────────────────────────────────────────────────────────

    @Test
    fun `Recover heals unit`() {
        val pos = Position(0, 0)
        val unit = Unit(id = 1, type = UnitType.WARRIOR, tribeId = 0, position = pos, currentHp = 5)
        val state = makeState(
            tiles = mapOf(pos to Tile(Terrain.PLAIN, unit = unit)),
            tribes = listOf(Tribe(0, TribeType.IMPERIUS)),
        )
        val result = ActionResolver.resolve(state, Action.Recover(unitId = 1))
        val healed = result.board.getUnit(pos)
        assertNotNull(healed)
        assertTrue((healed?.currentHp ?: 5) > 5)
        assertEquals(TurnStatus.FINISHED, healed?.turnStatus)
    }

    // ── Disband ───────────────────────────────────────────────────────────────

    @Test
    fun `Disband removes unit and refunds stars`() {
        val pos = Position(0, 0)
        val unit = warrior(1, 0, pos)
        val tribe = Tribe(0, TribeType.IMPERIUS, stars = 5)
        val state = makeState(
            tiles = mapOf(pos to Tile(Terrain.PLAIN, unit = unit)),
            tribes = listOf(tribe),
        )
        val result = ActionResolver.resolve(state, Action.Disband(unitId = 1))
        assertNull(result.board.getUnit(pos))
        val refund = UnitType.WARRIOR.cost / 2
        assertEquals(5 + refund, result.getTribe(0)?.stars)
    }

    // ── ResearchTech ──────────────────────────────────────────────────────────

    @Test
    fun `ResearchTech deducts stars and adds tech`() {
        val tribe = Tribe(0, TribeType.IMPERIUS, stars = 20)
        val state = makeState(
            tiles = mapOf(Position(0, 0) to Tile(Terrain.PLAIN)),
            tribes = listOf(tribe),
        )
        val cost = Technology.CLIMBING.cost(0, emptySet())
        val result = ActionResolver.resolve(state, Action.ResearchTech(tribeId = 0, technology = Technology.CLIMBING))
        assertTrue(result.getTribe(0)?.researchedTechs?.contains(Technology.CLIMBING) == true)
        assertEquals(20 - cost, result.getTribe(0)?.stars)
    }

    @Test
    fun `ResearchTech fails without prerequisite`() {
        val tribe = Tribe(0, TribeType.IMPERIUS, stars = 20)
        val state = makeState(
            tiles = mapOf(Position(0, 0) to Tile(Terrain.PLAIN)),
            tribes = listOf(tribe),
        )
        val result = ActionResolver.resolve(state, Action.ResearchTech(tribeId = 0, technology = Technology.ARCHERY))
        assertFalse(result.getTribe(0)?.researchedTechs?.contains(Technology.ARCHERY) == true)
    }

    @Test
    fun `ResearchTech fails with insufficient stars`() {
        val tribe = Tribe(0, TribeType.IMPERIUS, stars = 0)
        val state = makeState(
            tiles = mapOf(Position(0, 0) to Tile(Terrain.PLAIN)),
            tribes = listOf(tribe),
        )
        val result = ActionResolver.resolve(state, Action.ResearchTech(tribeId = 0, technology = Technology.CLIMBING))
        assertFalse(result.getTribe(0)?.researchedTechs?.contains(Technology.CLIMBING) == true)
    }

    // ── EndTurn ───────────────────────────────────────────────────────────────

    @Test
    fun `EndTurn advances to next tribe`() {
        val state = makeState(
            tiles = mapOf(Position(0, 0) to Tile(Terrain.PLAIN)),
            tribes = listOf(Tribe(0, TribeType.IMPERIUS), Tribe(1, TribeType.BARDUR)),
            currentTribeIndex = 0,
        )
        val result = ActionResolver.resolve(state, Action.EndTurn(tribeId = 0))
        assertEquals(1, result.currentTribeIndex)
        assertEquals(1, result.turn) // turn only advances after last tribe
    }

    @Test
    fun `EndTurn by last tribe increments turn`() {
        val state = makeState(
            tiles = mapOf(Position(0, 0) to Tile(Terrain.PLAIN)),
            tribes = listOf(Tribe(0, TribeType.IMPERIUS), Tribe(1, TribeType.BARDUR)),
            currentTribeIndex = 1,
        )
        val result = ActionResolver.resolve(state, Action.EndTurn(tribeId = 1))
        assertEquals(0, result.currentTribeIndex)
        assertEquals(2, result.turn)
    }

    @Test
    fun `EndTurn resets unit turn status to FRESH`() {
        val pos = Position(0, 0)
        val unit = warrior(1, 0, pos, TurnStatus.ATTACKED)
        val state = makeState(
            tiles = mapOf(pos to Tile(Terrain.PLAIN, unit = unit)),
            tribes = listOf(Tribe(0, TribeType.IMPERIUS)),
            currentTribeIndex = 0,
        )
        val result = ActionResolver.resolve(state, Action.EndTurn(tribeId = 0))
        assertEquals(TurnStatus.FRESH, result.board.getUnit(pos)?.turnStatus)
    }

    @Test
    fun `EndTurn collects star income from cities`() {
        val cityPos = Position(1, 0)
        val city = City(id = 10, position = cityPos, tribeId = 0, level = 2)
        val tribe = Tribe(0, TribeType.IMPERIUS, stars = 5, cityIds = listOf(10))
        val state = makeState(
            tiles = mapOf(
                Position(0, 0) to Tile(Terrain.PLAIN),
                cityPos to Tile(Terrain.CITY, cityId = 10),
            ),
            cities = mapOf(10 to city),
            tribes = listOf(tribe),
        )
        val result = ActionResolver.resolve(state, Action.EndTurn(tribeId = 0))
        assertEquals(5 + city.starIncome, result.getTribe(0)?.stars)
    }

    // ── SendStars ─────────────────────────────────────────────────────────────

    @Test
    fun `SendStars transfers stars between tribes`() {
        val state = makeState(
            tiles = mapOf(Position(0, 0) to Tile(Terrain.PLAIN)),
            tribes = listOf(Tribe(0, TribeType.IMPERIUS, stars = 20), Tribe(1, TribeType.BARDUR, stars = 5)),
        )
        val result = ActionResolver.resolve(state, Action.SendStars(tribeId = 0, targetTribeId = 1, amount = 10))
        assertEquals(10, result.getTribe(0)?.stars)
        assertEquals(15, result.getTribe(1)?.stars)
    }

    @Test
    fun `SendStars fails with insufficient stars`() {
        val state = makeState(
            tiles = mapOf(Position(0, 0) to Tile(Terrain.PLAIN)),
            tribes = listOf(Tribe(0, TribeType.IMPERIUS, stars = 5), Tribe(1, TribeType.BARDUR, stars = 5)),
        )
        val result = ActionResolver.resolve(state, Action.SendStars(tribeId = 0, targetTribeId = 1, amount = 10))
        assertEquals(5, result.getTribe(0)?.stars)
        assertEquals(5, result.getTribe(1)?.stars)
    }

    // ── LevelUp ───────────────────────────────────────────────────────────────

    @Test
    fun `LevelUp increases city level when population sufficient`() {
        val city = City(id = 10, position = Position(0, 0), tribeId = 0, population = 2)
        val state = makeState(
            tiles = mapOf(Position(0, 0) to Tile(Terrain.CITY, cityId = 10)),
            cities = mapOf(10 to city),
            tribes = listOf(Tribe(0, TribeType.IMPERIUS)),
        )
        val result = ActionResolver.resolve(state, Action.LevelUp(cityId = 10, choice = LevelUpChoice.WORKSHOP))
        assertEquals(2, result.board.getCity(10)?.level)
    }

    @Test
    fun `LevelUp with CITY_WALL adds walls`() {
        val city = City(id = 10, position = Position(0, 0), tribeId = 0, population = 2)
        val state = makeState(
            tiles = mapOf(Position(0, 0) to Tile(Terrain.CITY, cityId = 10)),
            cities = mapOf(10 to city),
            tribes = listOf(Tribe(0, TribeType.IMPERIUS)),
        )
        val result = ActionResolver.resolve(state, Action.LevelUp(cityId = 10, choice = LevelUpChoice.CITY_WALL))
        assertTrue(result.board.getCity(10)?.hasWalls == true)
    }

    // ── Spawn ─────────────────────────────────────────────────────────────────

    @Test
    fun `Spawn creates unit and deducts stars`() {
        val cityPos = Position(0, 0)
        val spawnPos = Position(1, 0)
        val city = City(id = 10, position = cityPos, tribeId = 0)
        val tribe = Tribe(0, TribeType.IMPERIUS, stars = 10, cityIds = listOf(10))
        val state = makeState(
            tiles = mapOf(
                cityPos to Tile(Terrain.CITY, cityId = 10),
                spawnPos to Tile(Terrain.PLAIN),
            ),
            cities = mapOf(10 to city),
            tribes = listOf(tribe),
        )
        val result = ActionResolver.resolve(state, Action.Spawn(cityId = 10, unitType = UnitType.WARRIOR, position = spawnPos))
        assertNotNull(result.board.getUnit(spawnPos))
        assertEquals(UnitType.WARRIOR, result.board.getUnit(spawnPos)?.type)
        assertEquals(10 - UnitType.WARRIOR.cost, result.getTribe(0)?.stars)
    }

    // ── Forest actions ────────────────────────────────────────────────────────

    @Test
    fun `ClearForest converts forest to plain and gives stars`() {
        val forestPos = Position(1, 0)
        val city = City(id = 10, position = Position(0, 0), tribeId = 0)
        val tribe = Tribe(0, TribeType.IMPERIUS, stars = 5)
        val state = makeState(
            tiles = mapOf(
                Position(0, 0) to Tile(Terrain.CITY, cityId = 10),
                forestPos to Tile(Terrain.FOREST),
            ),
            cities = mapOf(10 to city),
            tribes = listOf(tribe),
        )
        val result = ActionResolver.resolve(state, Action.ClearForest(cityId = 10, position = forestPos))
        assertEquals(Terrain.PLAIN, result.board.getTile(forestPos)?.terrain)
        assertEquals(7, result.getTribe(0)?.stars) // +2 stars
    }

    @Test
    fun `GrowForest converts plain to forest and costs stars`() {
        val plainPos = Position(1, 0)
        val city = City(id = 10, position = Position(0, 0), tribeId = 0)
        val tribe = Tribe(0, TribeType.IMPERIUS, stars = 10)
        val state = makeState(
            tiles = mapOf(
                Position(0, 0) to Tile(Terrain.CITY, cityId = 10),
                plainPos to Tile(Terrain.PLAIN),
            ),
            cities = mapOf(10 to city),
            tribes = listOf(tribe),
        )
        val result = ActionResolver.resolve(state, Action.GrowForest(cityId = 10, position = plainPos))
        assertEquals(Terrain.FOREST, result.board.getTile(plainPos)?.terrain)
        assertEquals(5, result.getTribe(0)?.stars) // -5 stars
    }

    // ── BuildRoad ─────────────────────────────────────────────────────────────

    @Test
    fun `BuildRoad adds road to tile and costs stars`() {
        val roadPos = Position(1, 0)
        val tribe = Tribe(0, TribeType.YADAKK, stars = 10, researchedTechs = setOf(Technology.ROADS))
        val state = makeState(
            tiles = mapOf(
                Position(0, 0) to Tile(Terrain.PLAIN),
                roadPos to Tile(Terrain.PLAIN),
            ),
            tribes = listOf(tribe),
        )
        val result = ActionResolver.resolve(state, Action.BuildRoad(tribeId = 0, position = roadPos))
        assertTrue(result.board.getTile(roadPos)?.hasRoad == true)
        assertEquals(8, result.getTribe(0)?.stars) // -2 stars
    }
}
