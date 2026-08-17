package engine

import engine.actions.Action
import engine.model.*
import engine.model.Unit
import engine.rules.ActionResolver
import engine.rules.CombatRules
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Board layout (6x6). Coordinates: Position(x=col, y=row), origin top-left.
 *
 *        x=0      x=1      x=2      x=3      x=4      x=5
 *  y=0 [ CAP0  ][ plain ][ RUINS ][ plain ][ plain ][ CAP1  ]
 *  y=1 [ plain ][ WAR0  ][ WAR1  ][ ARC1  ][ plain ][ plain ]
 *  y=2 [ plain ][ plain ][ mount ][ plain ][ plain ][ plain ]
 *  y=3 [ plain ][ plain ][ plain ][ FRUIT ][ plain ][ plain ]
 *  y=4 [ plain ][ plain ][ plain ][ plain ][ plain ][ plain ]
 *  y=5 [ CITY0 ][ plain ][ plain ][ plain ][ plain ][ CITY1 ]
 *
 *  Tribe 0 (IMPERIUS, id=0):
 *    - Capital city (id=10) at (0,0), level 1
 *    - City (id=11) at (0,5), level 1
 *    - Warrior WAR0 (id=1) at (1,1), FRESH
 *
 *  Tribe 1 (BARDUR, id=1):
 *    - Capital city (id=20) at (5,0), level 1
 *    - City (id=21) at (5,5), level 1
 *    - Warrior WAR1 (id=2) at (2,1), FRESH  ← adjacent to WAR0, melee range
 *    - Archer  ARC1 (id=3) at (3,1), FRESH  ← range 2, can reach WAR0 at (1,1)
 *
 *  Resources:
 *    - RUINS at (2,0)
 *    - FRUIT at (3,3)
 *
 *  Combat reference (equal warriors, full HP, open plain):
 *    attackForce = 2*(10/10) = 2.0, defenceForce = 2*(10/10) = 2.0
 *    attackDamage = (2/4)*2*4.5 = 4.5 → rounds to 5,  retaliationDamage = 5
 */
class GameStateTest {

    // ── Board / state factory ─────────────────────────────────────────────────

    private val cap0Pos  = Position(0, 0)
    private val cap1Pos  = Position(5, 0)
    private val city0Pos = Position(0, 5)
    private val city1Pos = Position(5, 5)
    private val war0Pos  = Position(1, 1)
    private val war1Pos  = Position(2, 1)
    private val arc1Pos  = Position(3, 1)
    private val ruinsPos = Position(2, 0)
    private val fruitPos = Position(3, 3)

    private val cap0  = City(id = 10, position = cap0Pos,  tribeId = 0, isCapital = true)
    private val cap1  = City(id = 20, position = cap1Pos,  tribeId = 1, isCapital = true)
    private val city0 = City(id = 11, position = city0Pos, tribeId = 0)
    private val city1 = City(id = 21, position = city1Pos, tribeId = 1)

    private val war0 = Unit(id = 1, type = UnitType.WARRIOR, tribeId = 0, position = war0Pos)
    private val war1 = Unit(id = 2, type = UnitType.WARRIOR, tribeId = 1, position = war1Pos)
    private val arc1 = Unit(id = 3, type = UnitType.ARCHER,  tribeId = 1, position = arc1Pos)

    private val tribe0 = Tribe(id = 0, type = TribeType.IMPERIUS, stars = 5, cityIds = listOf(10, 11))
    private val tribe1 = Tribe(id = 1, type = TribeType.BARDUR,   stars = 5, cityIds = listOf(20, 21))

    private fun buildBoard(): Board {
        val tiles = mapOf(
            // row 0
            cap0Pos          to Tile(Terrain.CITY,    cityId = 10),
            Position(1, 0)   to Tile(Terrain.PLAIN),
            ruinsPos         to Tile(Terrain.PLAIN,   resource = Resource.RUINS),
            Position(3, 0)   to Tile(Terrain.PLAIN),
            Position(4, 0)   to Tile(Terrain.PLAIN),
            cap1Pos          to Tile(Terrain.CITY,    cityId = 20),
            // row 1
            Position(0, 1)   to Tile(Terrain.PLAIN),
            war0Pos          to Tile(Terrain.PLAIN,   unit = war0),
            war1Pos          to Tile(Terrain.PLAIN,   unit = war1),
            arc1Pos          to Tile(Terrain.PLAIN,   unit = arc1),
            Position(4, 1)   to Tile(Terrain.PLAIN),
            Position(5, 1)   to Tile(Terrain.PLAIN),
            // row 2
            Position(0, 2)   to Tile(Terrain.PLAIN),
            Position(1, 2)   to Tile(Terrain.PLAIN),
            Position(2, 2)   to Tile(Terrain.MOUNTAIN),
            Position(3, 2)   to Tile(Terrain.PLAIN),
            Position(4, 2)   to Tile(Terrain.PLAIN),
            Position(5, 2)   to Tile(Terrain.PLAIN),
            // row 3
            Position(0, 3)   to Tile(Terrain.PLAIN),
            Position(1, 3)   to Tile(Terrain.PLAIN),
            Position(2, 3)   to Tile(Terrain.PLAIN),
            fruitPos         to Tile(Terrain.PLAIN,   resource = Resource.FRUIT),
            Position(4, 3)   to Tile(Terrain.PLAIN),
            Position(5, 3)   to Tile(Terrain.PLAIN),
            // row 4
            Position(0, 4)   to Tile(Terrain.PLAIN),
            Position(1, 4)   to Tile(Terrain.PLAIN),
            Position(2, 4)   to Tile(Terrain.PLAIN),
            Position(3, 4)   to Tile(Terrain.PLAIN),
            Position(4, 4)   to Tile(Terrain.PLAIN),
            Position(5, 4)   to Tile(Terrain.PLAIN),
            // row 5
            city0Pos         to Tile(Terrain.CITY,    cityId = 11),
            Position(1, 5)   to Tile(Terrain.PLAIN),
            Position(2, 5)   to Tile(Terrain.PLAIN),
            Position(3, 5)   to Tile(Terrain.PLAIN),
            Position(4, 5)   to Tile(Terrain.PLAIN),
            city1Pos         to Tile(Terrain.CITY,    cityId = 21),
        )
        val cities = mapOf(10 to cap0, 11 to city0, 20 to cap1, 21 to city1)
        return Board(tiles = tiles, cities = cities)
    }

    private fun buildState(): GameState =
        GameState(board = buildBoard(), tribes = listOf(tribe0, tribe1))

    // ── Basic state sanity ────────────────────────────────────────────────────

    @Nested
    inner class BasicState {

        @Test
        fun `GameState constructs with correct current tribe`() {
            val state = buildState()
            assertEquals(0, state.currentTribe.id)
            assertEquals(TribeType.IMPERIUS, state.currentTribe.type)
        }

        @Test
        fun `GameState turn starts at 1`() {
            assertEquals(1, buildState().turn)
        }

        @Test
        fun `Tribe starts with initial stars`() {
            val state = GameState(board = buildBoard(), tribes = listOf(
                Tribe(id = 0, type = TribeType.IMPERIUS),
                Tribe(id = 1, type = TribeType.BARDUR),
            ))
            assertEquals(Tribe.INITIAL_STARS, state.currentTribe.stars)
        }

        @Test
        fun `Board has all 36 tiles`() {
            assertEquals(36, buildBoard().tiles.size)
        }

        @Test
        fun `Board has 4 cities`() {
            assertEquals(4, buildBoard().cities.size)
        }

        @Test
        fun `Tribe 0 owns two cities`() {
            assertEquals(listOf(10, 11), buildState().tribes[0].cityIds)
        }

        @Test
        fun `Tribe 1 owns two cities`() {
            assertEquals(listOf(20, 21), buildState().tribes[1].cityIds)
        }

        @Test
        fun `WAR0 is on the board at (1,1)`() {
            val unit = buildBoard().getUnit(war0Pos)
            assertNotNull(unit)
            assertEquals(UnitType.WARRIOR, unit!!.type)
            assertEquals(0, unit.tribeId)
        }

        @Test
        fun `WAR1 is on the board at (2,1)`() {
            val unit = buildBoard().getUnit(war1Pos)
            assertNotNull(unit)
            assertEquals(UnitType.WARRIOR, unit!!.type)
            assertEquals(1, unit.tribeId)
        }

        @Test
        fun `ARC1 is on the board at (3,1)`() {
            val unit = buildBoard().getUnit(arc1Pos)
            assertNotNull(unit)
            assertEquals(UnitType.ARCHER, unit!!.type)
            assertEquals(1, unit.tribeId)
        }
    }

    // ── Tribe / Technology model ──────────────────────────────────────────────

    @Nested
    inner class TribeModel {

        @Test
        fun `Tribe canResearch tier-1 tech without prerequisites`() {
            val tribe = Tribe(id = 0, type = TribeType.IMPERIUS)
            assertTrue(tribe.canResearch(Technology.CLIMBING))
            assertTrue(tribe.canResearch(Technology.FISHING))
        }

        @Test
        fun `Tribe cannot research tier-2 tech without prerequisite`() {
            assertFalse(Tribe(id = 0, type = TribeType.IMPERIUS).canResearch(Technology.ARCHERY))
        }

        @Test
        fun `Tribe can research tier-2 tech after prerequisite`() {
            val tribe = Tribe(id = 0, type = TribeType.IMPERIUS, researchedTechs = setOf(Technology.HUNTING))
            assertTrue(tribe.canResearch(Technology.ARCHERY))
        }

        @Test
        fun `Tribe cannot research already-researched tech`() {
            val tribe = Tribe(id = 0, type = TribeType.IMPERIUS, researchedTechs = setOf(Technology.CLIMBING))
            assertFalse(tribe.canResearch(Technology.CLIMBING))
        }

        @Test
        fun `Technology cost increases with city count`() {
            assertTrue(Technology.CLIMBING.cost(3, emptySet()) > Technology.CLIMBING.cost(1, emptySet()))
        }

        @Test
        fun `Philosophy reduces technology cost`() {
            val without = Technology.CLIMBING.cost(2, emptySet())
            val with    = Technology.CLIMBING.cost(2, setOf(Technology.PHILOSOPHY))
            assertTrue(with < without)
        }
    }

    // ── Unit model ────────────────────────────────────────────────────────────

    @Nested
    inner class UnitModel {

        @Test
        fun `Unit starts at full HP`() {
            assertEquals(UnitType.WARRIOR.maxHp, war0.currentHp)
            assertEquals(UnitType.WARRIOR.maxHp, war0.maxHp)
        }

        @Test
        fun `Veteran unit has increased max HP`() {
            val vet = war0.copy(isVeteran = true)
            assertEquals(UnitType.WARRIOR.maxHp + Unit.VETERAN_PLUS_HP, vet.maxHp)
        }

        @Test
        fun `Unit canMove only when FRESH`() {
            assertTrue(war0.canMove())
            assertFalse(war0.withStatus(TurnStatus.MOVED).canMove())
            assertFalse(war0.withStatus(TurnStatus.ATTACKED).canMove())
        }

        @Test
        fun `Unit canAttack when FRESH or MOVED`() {
            assertTrue(war0.canAttack())
            assertTrue(war0.withStatus(TurnStatus.MOVED).canAttack())
            assertFalse(war0.withStatus(TurnStatus.ATTACKED).canAttack())
            assertFalse(war0.withStatus(TurnStatus.FINISHED).canAttack())
        }
    }

    // ── City model ────────────────────────────────────────────────────────────

    @Nested
    inner class CityModel {

        @Test
        fun `City starts at level 1 with populationNeed 2`() {
            assertEquals(1, cap0.level)
            assertEquals(2, cap0.populationNeed)
            assertFalse(cap0.canLevelUp())
        }

        @Test
        fun `City can level up when population meets need`() {
            val ready = cap0.copy(population = 2)
            assertTrue(ready.canLevelUp())
            val leveled = ready.leveled()
            assertEquals(2, leveled.level)
            assertEquals(0, leveled.population)
            assertEquals(3, leveled.populationNeed)
        }

        @Test
        fun `City production equals level plus production bonus`() {
            val city = City(id = 10, position = cap0Pos, tribeId = 0, level = 2, production = 1)
            assertEquals(3, city.starIncome)
        }

        @Test
        fun `Capital city gets production bonus`() {
            assertEquals(1 + City.PROD_CAPITAL_BONUS, cap0.starIncome)
        }
    }

    // ── Board model ───────────────────────────────────────────────────────────

    @Nested
    inner class BoardModel {

        @Test
        fun `Board returns correct tile`() {
            val board = buildBoard()
            assertEquals(Terrain.MOUNTAIN, board.getTile(Position(2, 2))?.terrain)
            assertNull(board.getTile(Position(10, 10)))
        }

        @Test
        fun `Board neighbors of (1,1) includes all 8 surrounding positions`() {
            val board = buildBoard()
            val neighbors = board.neighbors(war0Pos)
            // All 8 neighbors of (1,1) exist on the 6x6 board
            assertTrue(Position(0, 0) in neighbors)
            assertTrue(Position(1, 0) in neighbors)
            assertTrue(Position(2, 0) in neighbors)
            assertTrue(Position(0, 1) in neighbors)
            assertTrue(Position(2, 1) in neighbors)
            assertTrue(Position(0, 2) in neighbors)
            assertTrue(Position(1, 2) in neighbors)
            assertTrue(Position(2, 2) in neighbors)
        }

        @Test
        fun `Board neighbors of corner (0,0) returns only existing positions`() {
            val board = buildBoard()
            val neighbors = board.neighbors(cap0Pos)
            assertEquals(3, neighbors.size) // (1,0), (0,1), (1,1)
            assertTrue(Position(1, 0) in neighbors)
            assertTrue(Position(0, 1) in neighbors)
            assertTrue(Position(1, 1) in neighbors)
        }

        @Test
        fun `Position chebyshevDistance is correct`() {
            assertEquals(1, war0Pos.chebyshevDistance(war1Pos))   // (1,1)→(2,1)
            assertEquals(2, war0Pos.chebyshevDistance(arc1Pos))   // (1,1)→(3,1)
            assertEquals(0, war0Pos.chebyshevDistance(war0Pos))
            assertEquals(2, Position(0, 0).chebyshevDistance(Position(2, 1)))
        }
    }

    // ── Available actions for WAR0 (tribe 0's warrior at (1,1)) ──────────────

    @Nested
    inner class War0Actions {

        @Test
        fun `WAR0 has Move actions to reachable empty adjacent tiles`() {
            val state = buildState()
            val actions = state.getUserActions(TileKind.Unit, war0Pos)
            val moves = actions.filterIsInstance<Action.Move>()
            assertTrue(moves.isNotEmpty(), "WAR0 should have move actions")
            assertTrue(moves.all { it.unitId == war0.id })
            // (0,1) and (1,0) are empty plain tiles adjacent to (1,1)
            assertTrue(moves.any { it.destination == Position(0, 1) })
            assertTrue(moves.any { it.destination == Position(1, 0) })
        }

        @Test
        fun `WAR0 cannot move to occupied tile (war1 at (2,1))`() {
            val state = buildState()
            val moves = state.getUserActions(TileKind.Unit, war0Pos).filterIsInstance<Action.Move>()
            assertFalse(moves.any { it.destination == war1Pos }, "Cannot move onto occupied tile")
        }

        @Test
        fun `WAR0 has Attack action targeting WAR1 (adjacent enemy)`() {
            val state = buildState()
            val attacks = state.getUserActions(TileKind.Unit, war0Pos).filterIsInstance<Action.Attack>()
            assertTrue(attacks.any { it.targetId == war1.id }, "WAR0 should be able to attack WAR1")
        }

        @Test
        fun `WAR0 has no Attack action targeting ARC1 (distance 2, out of melee range)`() {
            val state = buildState()
            val attacks = state.getUserActions(TileKind.Unit, war0Pos).filterIsInstance<Action.Attack>()
            assertFalse(attacks.any { it.targetId == arc1.id }, "WAR0 cannot reach ARC1 at range 2")
        }

        @Test
        fun `WAR0 has Recover action when damaged`() {
            val damagedWar0 = war0.copy(currentHp = 5)
            val board = buildBoard().withUnit(war0Pos, damagedWar0)
            val state = buildState().withBoard(board)
            val actions = state.getUserActions(TileKind.Unit, war0Pos)
            assertTrue(actions.filterIsInstance<Action.Recover>().isNotEmpty())
        }

        @Test
        fun `WAR0 has no Recover action at full HP`() {
            val state = buildState()
            val actions = state.getUserActions(TileKind.Unit, war0Pos)
            assertTrue(actions.filterIsInstance<Action.Recover>().isEmpty())
        }

        @Test
        fun `WAR0 after MOVED status has no Move actions but can still Attack`() {
            val movedWar0 = war0.withStatus(TurnStatus.MOVED)
            val board = buildBoard().withUnit(war0Pos, movedWar0)
            val state = buildState().withBoard(board)
            val actions = state.getUserActions(TileKind.Unit, war0Pos)
            assertTrue(actions.filterIsInstance<Action.Move>().isEmpty())
            assertTrue(actions.filterIsInstance<Action.Attack>().any { it.targetId == war1.id })
        }

        @Test
        fun `WAR0 after ATTACKED status has no Move or Attack actions`() {
            val attackedWar0 = war0.withStatus(TurnStatus.ATTACKED)
            val board = buildBoard().withUnit(war0Pos, attackedWar0)
            val state = buildState().withBoard(board)
            val actions = state.getUserActions(TileKind.Unit, war0Pos)
            assertTrue(actions.filterIsInstance<Action.Move>().isEmpty())
            assertTrue(actions.filterIsInstance<Action.Attack>().isEmpty())
        }

        @Test
        fun `Enemy unit at WAR0 position returns no actions for tribe 0`() {
            val state = buildState()
            // Querying enemy unit position returns empty (wrong tribe)
            val actions = state.getUserActions(TileKind.Unit, war1Pos)
            assertTrue(actions.isEmpty(), "Tribe 0 cannot act on tribe 1 unit")
        }
    }

    // ── Available actions for ARC1 (tribe 1's archer at (3,1)) ───────────────

    @Nested
    inner class Arc1Actions {

        private fun stateAsTribe1(): GameState {
            // Advance to tribe 1's turn
            val state = buildState()
            return ActionResolver.resolve(state, Action.EndTurn(0))
        }

        @Test
        fun `ARC1 has Attack action targeting WAR0 (distance 2, within archer range)`() {
            val state = stateAsTribe1()
            val actions = state.getUserActions(TileKind.Unit, arc1Pos)
            val attacks = actions.filterIsInstance<Action.Attack>()
            assertTrue(attacks.any { it.targetId == war0.id }, "ARC1 should be able to attack WAR0 at range 2")
        }

        @Test
        fun `ARC1 has Move actions to empty adjacent tiles`() {
            val state = stateAsTribe1()
            val moves = state.getUserActions(TileKind.Unit, arc1Pos).filterIsInstance<Action.Move>()
            assertTrue(moves.isNotEmpty())
            assertTrue(moves.all { it.unitId == arc1.id })
        }

        @Test
        fun `ARC1 cannot move to tile occupied by WAR1`() {
            val state = stateAsTribe1()
            val moves = state.getUserActions(TileKind.Unit, arc1Pos).filterIsInstance<Action.Move>()
            assertFalse(moves.any { it.destination == war1Pos })
        }
    }

    // ── Tribe-level available actions ─────────────────────────────────────────

    @Nested
    inner class TribeActions {

        @Test
        fun `getUserActions always includes EndTurn`() {
            val actions = buildState().getUserActions()
            assertTrue(actions.filterIsInstance<Action.EndTurn>().isNotEmpty())
        }

        @Test
        fun `getUserActions includes Move and Attack actions from WAR0`() {
            val actions = buildState().getUserActions()
            assertTrue(actions.filterIsInstance<Action.Move>().any { it.unitId == war0.id })
            assertTrue(actions.filterIsInstance<Action.Attack>().any { it.unitId == war0.id })
        }

        @Test
        fun `getUserActions returns ResearchTech for affordable techs when stars sufficient`() {
            val richTribe = tribe0.copy(stars = 20)
            val state = buildState().withTribe(richTribe)
            assertTrue(state.getUserActions().filterIsInstance<Action.ResearchTech>().isNotEmpty())
        }

        @Test
        fun `getUserActions returns no ResearchTech when stars insufficient`() {
            val brokeTribe = tribe0.copy(stars = 0)
            val state = buildState().withTribe(brokeTribe)
            assertTrue(state.getUserActions().filterIsInstance<Action.ResearchTech>().isEmpty())
        }

        @Test
        fun `getUserActions includes LevelUp for city with sufficient population`() {
            val readyCity = cap0.copy(population = 2)
            val richTribe = tribe0.copy(stars = 20)
            val board = buildBoard().withCity(readyCity)
            val state = GameState(board = board, tribes = listOf(richTribe, tribe1))
            val actions = state.getUserActions()
            assertTrue(actions.filterIsInstance<Action.LevelUp>().any { it.cityId == cap0.id })
        }
    }

    // ── Attack consequences: WAR0 attacks WAR1 ───────────────────────────────

    @Nested
    inner class AttackWar0VsWar1 {

        // Both warriors: atk=2, def=2, hp=10, plain terrain, no bonuses
        // attackForce=2.0, defenceForce=2.0, total=4.0
        // attackDamage = (2/4)*2*4.5 = 4.5 → rounds to 5,  retaliationDamage = 5
        private val expectedAttackDamage = 5
        private val expectedRetaliationDamage = 5

        private fun afterAttack(): GameState {
            val state = buildState()
            return ActionResolver.resolve(state, Action.Attack(war0.id, war1.id))
        }

        @Test
        fun `WAR1 loses HP equal to attackDamage`() {
            val newState = afterAttack()
            val newWar1 = newState.board.getUnit(war1Pos)
            assertNotNull(newWar1, "WAR1 should still be alive")
            assertEquals(war1.currentHp - expectedAttackDamage, newWar1!!.currentHp)
        }

        @Test
        fun `WAR0 loses HP equal to retaliationDamage`() {
            val newState = afterAttack()
            val newWar0 = newState.board.getUnit(war0Pos)
            assertNotNull(newWar0, "WAR0 should still be alive after retaliation")
            assertEquals(war0.currentHp - expectedRetaliationDamage, newWar0!!.currentHp)
        }

        @Test
        fun `WAR0 status becomes ATTACKED after attacking`() {
            val newState = afterAttack()
            val newWar0 = newState.board.getUnit(war0Pos)
            assertEquals(TurnStatus.ATTACKED, newWar0!!.turnStatus)
        }

        @Test
        fun `WAR0 stays at (1,1) — melee attacker does not move when target survives`() {
            val newState = afterAttack()
            assertNotNull(newState.board.getUnit(war0Pos), "WAR0 should remain at (1,1)")
            assertNull(newState.board.getUnit(war1Pos)?.let {
                if (it.tribeId == 0) it else null
            }, "WAR0 should not be at WAR1's position")
        }

        @Test
        fun `WAR1 stays at (2,1) — target does not move`() {
            val newState = afterAttack()
            val unitAtWar1Pos = newState.board.getUnit(war1Pos)
            assertNotNull(unitAtWar1Pos)
            assertEquals(1, unitAtWar1Pos!!.tribeId)
        }

        @Test
        fun `ARC1 is unaffected by WAR0 vs WAR1 combat`() {
            val newState = afterAttack()
            val newArc1 = newState.board.getUnit(arc1Pos)
            assertEquals(arc1, newArc1)
        }

        @Test
        fun `Tribe stars are unaffected by combat`() {
            val newState = afterAttack()
            assertEquals(tribe0.stars, newState.tribes[0].stars)
            assertEquals(tribe1.stars, newState.tribes[1].stars)
        }

        @Test
        fun `Turn and currentTribeIndex are unaffected by combat`() {
            val newState = afterAttack()
            assertEquals(1, newState.turn)
            assertEquals(0, newState.currentTribeIndex)
        }

        @Test
        fun `All other tiles remain unchanged after combat`() {
            val before = buildState()
            val after = afterAttack()
            // Spot-check tiles not involved in combat
            assertEquals(before.board.getTile(cap0Pos), after.board.getTile(cap0Pos))
            assertEquals(before.board.getTile(cap1Pos), after.board.getTile(cap1Pos))
            assertEquals(before.board.getTile(fruitPos), after.board.getTile(fruitPos))
            assertEquals(before.board.getTile(ruinsPos), after.board.getTile(ruinsPos))
            assertEquals(before.board.getTile(Position(2, 2)), after.board.getTile(Position(2, 2)))
        }
    }

    // ── Attack consequences: WAR0 kills WAR1 (WAR1 at low HP) ────────────────

    @Nested
    inner class AttackWar0KillsWar1 {

        // Set WAR1 HP to 4 so attackDamage=5 kills it
        private val dyingWar1 = war1.copy(currentHp = 4)

        private fun stateWithDyingWar1(): GameState {
            val board = buildBoard().withUnit(war1Pos, dyingWar1)
            return GameState(board = board, tribes = listOf(tribe0, tribe1))
        }

        private fun afterKill(): GameState {
            val state = stateWithDyingWar1()
            return ActionResolver.resolve(state, Action.Attack(war0.id, dyingWar1.id))
        }

        @Test
        fun `WAR1 is removed from board after being killed`() {
            val newState = afterKill()
            // WAR0 advances to war1Pos after kill; no tribe-1 unit should remain anywhere
            val allUnits = newState.board.allUnits()
            assertTrue(allUnits.none { (_, u) -> u.id == dyingWar1.id }, "WAR1 should be dead and gone")
        }

        @Test
        fun `WAR0 moves to WAR1 position after killing (melee advance)`() {
            val newState = afterKill()
            val unitAtWar1Pos = newState.board.getUnit(war1Pos)
            assertNotNull(unitAtWar1Pos, "WAR0 should advance to (2,1)")
            assertEquals(0, unitAtWar1Pos!!.tribeId)
        }

        @Test
        fun `WAR0 original position (1,1) is empty after melee advance`() {
            val newState = afterKill()
            assertNull(newState.board.getUnit(war0Pos), "WAR0 should have left (1,1)")
        }

        @Test
        fun `WAR0 gains a kill after defeating WAR1`() {
            val newState = afterKill()
            val newWar0 = newState.board.getUnit(war1Pos)!!
            assertEquals(1, newWar0.kills)
        }

        @Test
        fun `WAR0 takes no retaliation damage when killing (target dies before retaliating)`() {
            // When target is killed, no retaliation occurs — WAR0 HP unchanged
            val newState = afterKill()
            val newWar0 = newState.board.getUnit(war1Pos)!!
            assertEquals(war0.currentHp, newWar0.currentHp)
        }

        @Test
        fun `ARC1 is unaffected when WAR0 kills WAR1`() {
            val newState = afterKill()
            assertEquals(arc1, newState.board.getUnit(arc1Pos))
        }
    }

    // ── Attack consequences: ARC1 attacks WAR0 (ranged, no retaliation) ──────

    @Nested
    inner class AttackArc1VsWar0 {

        // ARC1: atk=2, def=1, hp=10, range=2. WAR0: atk=2, def=2, hp=10
        // attackForce=2.0, defenceForce=2.0, total=4.0
        // attackDamage=(2/4)*2*4.5=4.5 → rounds to 5
        // Retaliation: WAR0 range=1, distance=2 → no retaliation
        private val expectedAttackDamage = 5

        private fun stateAsTribe1(): GameState =
            ActionResolver.resolve(buildState(), Action.EndTurn(0))

        private fun afterArc1Attack(): GameState {
            val state = stateAsTribe1()
            return ActionResolver.resolve(state, Action.Attack(arc1.id, war0.id))
        }

        @Test
        fun `WAR0 loses HP equal to ARC1 attackDamage`() {
            val newState = afterArc1Attack()
            val newWar0 = newState.board.getUnit(war0Pos)
            assertNotNull(newWar0)
            assertEquals(war0.currentHp - expectedAttackDamage, newWar0!!.currentHp)
        }

        @Test
        fun `ARC1 takes no retaliation damage (WAR0 range 1 cannot reach ARC1 at distance 2)`() {
            val newState = afterArc1Attack()
            val newArc1 = newState.board.getUnit(arc1Pos)
            assertNotNull(newArc1)
            assertEquals(arc1.currentHp, newArc1!!.currentHp)
        }

        @Test
        fun `ARC1 status becomes ATTACKED after attacking`() {
            val newState = afterArc1Attack()
            val newArc1 = newState.board.getUnit(arc1Pos)
            assertEquals(TurnStatus.ATTACKED, newArc1!!.turnStatus)
        }

        @Test
        fun `ARC1 stays at (3,1) — ranged units do not advance`() {
            val newState = afterArc1Attack()
            assertNotNull(newState.board.getUnit(arc1Pos))
            assertEquals(1, newState.board.getUnit(arc1Pos)!!.tribeId)
        }

        @Test
        fun `WAR0 stays at (1,1) after being attacked by ARC1`() {
            val newState = afterArc1Attack()
            assertNotNull(newState.board.getUnit(war0Pos))
        }

        @Test
        fun `WAR1 is unaffected by ARC1 vs WAR0 combat`() {
            val newState = afterArc1Attack()
            assertEquals(war1, newState.board.getUnit(war1Pos))
        }

        @Test
        fun `Cities and resources are unaffected by ARC1 vs WAR0 combat`() {
            val before = buildState()
            val after = afterArc1Attack()
            assertEquals(before.board.cities, after.board.cities)
            assertEquals(before.board.getTile(ruinsPos), after.board.getTile(ruinsPos))
            assertEquals(before.board.getTile(fruitPos), after.board.getTile(fruitPos))
        }
    }
}
