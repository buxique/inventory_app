package com.example.inventory.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class UndoRedoManagerTest {

    private lateinit var undoRedoManager: UndoRedoManager<String>

    @Before
    fun setup() {
        undoRedoManager = UndoRedoManager(maxHistorySize = 5)
    }

    @Test
    fun `execute adds action to history`() = runTest {
        val action = TestAction("Action 1", "State 1")
        
        undoRedoManager.execute(action)
        
        assertEquals(1, undoRedoManager.getHistorySize())
        assertEquals(0, undoRedoManager.getRedoSize())
        assertTrue(undoRedoManager.canUndo.first())
        assertFalse(undoRedoManager.canRedo.first())
        assertEquals(listOf("Action 1"), undoRedoManager.getUndoHistory())
    }

    @Test
    fun `undo reverts action and moves to redo stack`() = runTest {
        val action = TestAction("Action 1", "State 1")
        undoRedoManager.execute(action)
        
        val result = undoRedoManager.undo()
        
        assertEquals("Undo: State 1", result)
        assertEquals(0, undoRedoManager.getHistorySize())
        assertEquals(1, undoRedoManager.getRedoSize())
        assertFalse(undoRedoManager.canUndo.first())
        assertTrue(undoRedoManager.canRedo.first())
        assertEquals(listOf("Action 1"), undoRedoManager.getRedoHistory())
    }

    @Test
    fun `redo re-executes action and moves to history stack`() = runTest {
        val action = TestAction("Action 1", "State 1")
        undoRedoManager.execute(action)
        undoRedoManager.undo()
        
        val result = undoRedoManager.redo()
        
        assertEquals("Execute: State 1", result)
        assertEquals(1, undoRedoManager.getHistorySize())
        assertEquals(0, undoRedoManager.getRedoSize())
        assertTrue(undoRedoManager.canUndo.first())
        assertFalse(undoRedoManager.canRedo.first())
    }

    @Test
    fun `history size limit is respected`() = runTest {
        val smallManager = UndoRedoManager<String>(maxHistorySize = 2)
        
        smallManager.execute(TestAction("1", "1"))
        smallManager.execute(TestAction("2", "2"))
        smallManager.execute(TestAction("3", "3"))
        
        assertEquals(2, smallManager.getHistorySize())
        assertEquals(listOf("2", "3"), smallManager.getUndoHistory())
    }

    @Test
    fun `execute clears redo stack`() = runTest {
        undoRedoManager.execute(TestAction("1", "1"))
        undoRedoManager.undo()
        
        // Redo stack has 1 item
        assertEquals(1, undoRedoManager.getRedoSize())
        
        // Execute new action
        undoRedoManager.execute(TestAction("2", "2"))
        
        // Redo stack should be cleared
        assertEquals(0, undoRedoManager.getRedoSize())
        assertEquals(1, undoRedoManager.getHistorySize())
    }
    
    @Test
    fun `clear removes all history`() = runTest {
        undoRedoManager.execute(TestAction("1", "1"))
        undoRedoManager.undo()
        undoRedoManager.execute(TestAction("2", "2"))
        
        undoRedoManager.clear()
        
        assertEquals(0, undoRedoManager.getHistorySize())
        assertEquals(0, undoRedoManager.getRedoSize())
        assertFalse(undoRedoManager.canUndo.first())
        assertFalse(undoRedoManager.canRedo.first())
    }

    @Test
    fun `concurrent execution is thread safe`() = runTest {
        val count = 100
        val jobs = List(count) { index ->
            launch(Dispatchers.Default) {
                undoRedoManager.execute(TestAction("Action $index", "$index"))
            }
        }
        jobs.joinAll()
        
        // Max history is 5 (from setup), so we should have 5 items
        assertEquals(5, undoRedoManager.getHistorySize())
        assertTrue(undoRedoManager.canUndo.first())
    }

    private class TestAction(
        override val description: String,
        private val state: String
    ) : UndoRedoManager.UndoableAction<String> {
        override suspend fun execute(): String {
            return "Execute: $state"
        }

        override suspend fun undo(): String {
            return "Undo: $state"
        }
    }
}
