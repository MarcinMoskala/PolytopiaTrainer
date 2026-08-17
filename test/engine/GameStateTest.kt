package engine

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameStateTest {
    // Sample game state with a map
    val state = GameState(
        // ...
    )
    
    @Test
    fun `gives correct unit actions`() {
        val actions: List<UserAction> = state.getUserActions(
            Tile.Unit,
            unitPosition,
        )
        
        val expectedActions = listOf<UserAction>(
            // ...
        )
        
        assertEquals(expectedActions, actions)
    }
    
    @Test
    fun `gives correct map actions`() {
        val actions: List<UserAction> = state.getUserActions(
            Tile.Map,
            unitPosition,
        )
        
        val expectedActions = listOf<UserAction>(
            // ...
        )
        
        assertEquals(expectedActions, actions)
    }
    
    @Test
    fun `gives correct user actions`() {
        val actions: List<UserAction> = state.getUserActions()
        
        val expectedActions = listOf<UserAction>(
            // ...
        )
        
        assertEquals(expectedActions, actions)
    }
}
