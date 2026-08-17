package engine

import engine.actions.Action
import engine.actions.LevelUpChoice
import engine.model.*
import engine.model.Unit
import engine.rules.ActionResolver
import engine.rules.EconomyRules
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EconomyRulesTest {

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

    // ── Star income ───────────────────────────────────────────────────────────

    @Test
    fun `starIncome for level-1 city is 1`() {
        val city = City(id = 10, position = Position(0, 0), tribeId = 0, level = 1)
        val tribe = Tribe(0, TribeType.IMPERIUS, cityIds = listOf(10))
        val board = Board(
            tiles = mapOf(Position(0, 0) to Tile(Terrain.CITY, cityId = 10)),
            cities = mapOf(10 to city),
        )
        assertEquals(1, EconomyRules.starIncome(tribe, board))
    }

    @Test
    fun `starIncome for level-2 city is 2`() {
        val city = City(id = 10, position = Position(0, 0), tribeId = 0, level = 2)
        val tribe = Tribe(0, TribeType.IMPERIUS, cityIds = listOf(10))
        val board = Board(
            tiles = mapOf(Position(0, 0) to Tile(Terrain.CITY, cityId = 10)),
            cities = mapOf(10 to city),
        )
        assertEquals(2, EconomyRules.starIncome(tribe, board))
    }

    @Test
    fun `starIncome for capital city includes bonus`() {
        val city = City(id = 10, position = Position(0, 0), tribeId = 0, level = 1, isCapital = true)
        val tribe = Tribe(0, TribeType.IMPERIUS, cityIds = listOf(10))
        val board = Board(
            tiles = mapOf(Position(0, 0) to Tile(Terrain.CITY, cityId = 10)),
            cities = mapOf(10 to city),
        )
        assertEquals(1 + City.PROD_CAPITAL_BONUS, EconomyRules.starIncome(tribe, board))
    }

    @Test
    fun `starIncome sums across multiple cities`() {
        val city1 = City(id = 10, position = Position(0, 0), tribeId = 0, level = 1)
        val city2 = City(id = 11, position = Position(5, 0), tribeId = 0, level = 3)
        val tribe = Tribe(0, TribeType.IMPERIUS, cityIds = listOf(10, 11))
        val board = Board(
            tiles = mapOf(
                Position(0, 0) to Tile(Terrain.CITY, cityId = 10),
                Position(5, 0) to Tile(Terrain.CITY, cityId = 11),
            ),
            cities = mapOf(10 to city1, 11 to city2),
        )
        assertEquals(1 + 3, EconomyRules.starIncome(tribe, board))
    }

    // ── Trade network ─────────────────────────────────────────────────────────

    @Test
    fun `tradeNetworkBonus is 0 without TRADE tech`() {
        val city = City(id = 10, position = Position(0, 0), tribeId = 0, isCapital = true)
        val tribe = Tribe(0, TribeType.IMPERIUS, cityIds = listOf(10))
        val board = Board(
            tiles = mapOf(Position(0, 0) to Tile(Terrain.CITY, cityId = 10)),
            cities = mapOf(10 to city),
        )
        assertEquals(0, EconomyRules.tradeNetworkBonus(tribe, board))
    }

    @Test
    fun `tradeNetworkBonus counts connected cities via roads`() {
        val capitalPos = Position(0, 0)
        val roadPos = Position(1, 0)
        val city2Pos = Position(2, 0)
        val capital = City(id = 10, position = capitalPos, tribeId = 0, isCapital = true)
        val city2 = City(id = 11, position = city2Pos, tribeId = 0)
        val tribe = Tribe(0, TribeType.IMPERIUS,
            researchedTechs = setOf(Technology.ROADS, Technology.TRADE),
            cityIds = listOf(10, 11))
        val board = Board(
            tiles = mapOf(
                capitalPos to Tile(Terrain.CITY, cityId = 10),
                roadPos to Tile(Terrain.PLAIN, hasRoad = true),
                city2Pos to Tile(Terrain.CITY, cityId = 11),
            ),
            cities = mapOf(10 to capital, 11 to city2),
        )
        val bonus = EconomyRules.tradeNetworkBonus(tribe, board)
        assertTrue(bonus > 0, "Connected cities should give trade bonus")
    }

    // ── Population thresholds ─────────────────────────────────────────────────

    @Test
    fun `populationNeedForLevel returns level+1`() {
        assertEquals(2, EconomyRules.populationNeedForLevel(1))
        assertEquals(3, EconomyRules.populationNeedForLevel(2))
        assertEquals(5, EconomyRules.populationNeedForLevel(4))
    }

    // ── Multi-turn integration ────────────────────────────────────────────────

    @Test
    fun `stars accumulate over multiple EndTurn cycles`() {
        val cityPos = Position(0, 0)
        val city = City(id = 10, position = cityPos, tribeId = 0, level = 2)
        val tribe = Tribe(0, TribeType.IMPERIUS, stars = 0, cityIds = listOf(10))
        val state = makeState(
            tiles = mapOf(cityPos to Tile(Terrain.CITY, cityId = 10)),
            cities = mapOf(10 to city),
            tribes = listOf(tribe),
        )
        // End turn twice (single tribe, so turn advances each time)
        val after1 = ActionResolver.resolve(state, Action.EndTurn(tribeId = 0))
        val after2 = ActionResolver.resolve(after1, Action.EndTurn(tribeId = 0))
        val income = city.starIncome
        assertEquals(income * 2, after2.getTribe(0)?.stars)
    }

    @Test
    fun `city levels up after enough population and unit can be spawned`() {
        val cityPos = Position(0, 0)
        val spawnPos = Position(1, 0)
        val city = City(id = 10, position = cityPos, tribeId = 0, level = 1, population = 2)
        val tribe = Tribe(0, TribeType.IMPERIUS, stars = 10, cityIds = listOf(10))
        var state = makeState(
            tiles = mapOf(
                cityPos to Tile(Terrain.CITY, cityId = 10),
                spawnPos to Tile(Terrain.PLAIN),
            ),
            cities = mapOf(10 to city),
            tribes = listOf(tribe),
        )
        // Level up city
        state = ActionResolver.resolve(state, Action.LevelUp(cityId = 10, choice = LevelUpChoice.WORKSHOP))
        assertEquals(2, state.board.getCity(10)?.level)

        // Spawn a warrior
        state = ActionResolver.resolve(state, Action.Spawn(cityId = 10, unitType = UnitType.WARRIOR, position = spawnPos))
        assertNotNull(state.board.getUnit(spawnPos))
        assertEquals(UnitType.WARRIOR, state.board.getUnit(spawnPos)?.type)
    }

    @Test
    fun `ResearchTech then Spawn unlocks new unit type`() {
        val cityPos = Position(0, 0)
        val spawnPos = Position(1, 0)
        val city = City(id = 10, position = cityPos, tribeId = 0)
        val tribe = Tribe(0, TribeType.IMPERIUS, stars = 30, cityIds = listOf(10))
        var state = makeState(
            tiles = mapOf(
                cityPos to Tile(Terrain.CITY, cityId = 10),
                spawnPos to Tile(Terrain.PLAIN),
            ),
            cities = mapOf(10 to city),
            tribes = listOf(tribe),
        )
        // Research RIDING to unlock RIDER
        state = ActionResolver.resolve(state, Action.ResearchTech(tribeId = 0, technology = Technology.RIDING))
        assertTrue(state.getTribe(0)?.hasResearched(Technology.RIDING) == true)

        // Spawn a Rider
        state = ActionResolver.resolve(state, Action.Spawn(cityId = 10, unitType = UnitType.RIDER, position = spawnPos))
        assertNotNull(state.board.getUnit(spawnPos))
        assertEquals(UnitType.RIDER, state.board.getUnit(spawnPos)?.type)
    }

    @Test
    fun `full turn cycle with two tribes advances correctly`() {
        val state = makeState(
            tiles = mapOf(Position(0, 0) to Tile(Terrain.PLAIN)),
            tribes = listOf(
                Tribe(0, TribeType.IMPERIUS, stars = 0),
                Tribe(1, TribeType.BARDUR, stars = 0),
            ),
        )
        assertEquals(1, state.turn)
        assertEquals(0, state.currentTribeIndex)

        val after0 = ActionResolver.resolve(state, Action.EndTurn(tribeId = 0))
        assertEquals(1, after0.turn)
        assertEquals(1, after0.currentTribeIndex)

        val after1 = ActionResolver.resolve(after0, Action.EndTurn(tribeId = 1))
        assertEquals(2, after1.turn)
        assertEquals(0, after1.currentTribeIndex)
    }
}
