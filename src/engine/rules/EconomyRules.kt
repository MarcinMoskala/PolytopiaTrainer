package engine.rules

import engine.model.*

object EconomyRules {

    /**
     * Computes the total star income for a tribe at end of turn.
     * Income = sum of each city's production (level + building bonuses + capital bonus)
     * plus trade network bonus for connected cities.
     */
    fun starIncome(tribe: Tribe, board: Board): Int {
        val cityIncomes = tribe.cityIds.mapNotNull { board.getCity(it) }.sumOf { it.starIncome }
        val tradeBonus = tradeNetworkBonus(tribe, board)
        return cityIncomes + tradeBonus
    }

    /**
     * Connected cities (reachable via roads or ports within PORT_TRADE_DISTANCE) each
     * contribute +1 star via the trade network. Simplified: count cities connected by road.
     */
    fun tradeNetworkBonus(tribe: Tribe, board: Board): Int {
        if (!tribe.hasResearched(Technology.TRADE)) return 0
        val connectedCities = connectedCityCount(tribe, board)
        return if (connectedCities > 1) connectedCities else 0
    }

    /**
     * Returns the number of tribe cities reachable from the capital via road tiles.
     */
    private fun connectedCityCount(tribe: Tribe, board: Board): Int {
        val capitalCity = tribe.cityIds.mapNotNull { board.getCity(it) }.firstOrNull { it.isCapital }
            ?: return 0
        val visited = mutableSetOf<Position>()
        val frontier = mutableListOf(capitalCity.position)
        var connected = 0
        while (frontier.isNotEmpty()) {
            val pos = frontier.removeFirst()
            if (!visited.add(pos)) continue
            val tile = board.getTile(pos) ?: continue
            if (tile.hasRoad || tile.terrain == Terrain.CITY) {
                if (tile.cityId != null && tribe.controlsCity(tile.cityId)) connected++
                board.neighbors(pos).filter { it !in visited }.forEach { frontier += it }
            }
        }
        return connected
    }

    /**
     * Computes the population threshold needed to level up from the given level.
     * Level n city needs (n+1) population to level up.
     */
    fun populationNeedForLevel(level: Int): Int = level + 1

    /**
     * Returns the star cost to level up a city (free — leveling up costs population, not stars).
     */
    fun levelUpCost(): Int = 0

    /**
     * Returns the building bonus (population or production) for a given building type.
     */
    fun buildingBonus(building: Building): Int = building.bonus
}
