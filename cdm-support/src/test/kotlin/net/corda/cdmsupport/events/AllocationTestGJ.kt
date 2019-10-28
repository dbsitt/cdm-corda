package net.corda.cdmsupport.events

import com.derivhack.AllocationFlowJson
import com.derivhack.ExecutionFlow
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AllocationTestGJ : BaseEventTestGJ() {

    @Test
    fun allocation() {

        // --------- new trade
        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/UC1_block_execute_BT1_GJ.json");
        val future1 = node2.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        future1.getOrThrow().toLedgerTransaction(node2.services)

        //----------------allocation
        //val allocationEvent = readEventFromJson("/${samplesDirectory}/UC2_allocation_execution_AT1.json")
        val jsonText2 = readTextFromFile("/${samplesDirectory}/UC2_allocation_execution_AT1_GJ.json")
        val future2 = node2.services.startFlow(AllocationFlowJson(jsonText2)).resultFuture
        val tx = future2.getOrThrow().toLedgerTransaction(node2.services)
        checkTheBasicFabricOfTheTransaction(tx, 1, 3, 0, 3)

        assertTrue(tx.commands.get(0).value is CDMEvent.Commands.Execution)
        assertTrue(tx.commands.get(1).value is CDMEvent.Commands.Execution)
        assertTrue(tx.commands.get(2).value is CDMEvent.Commands.Execution)

        //look closer at the states
        val executionInputState = tx.inputStates.find { it is ExecutionState } as ExecutionState
        val cdmExecutionInputState = executionInputState.execution()
        assertNotNull(cdmExecutionInputState)
        checkIdentiferIsOnTrade(cdmExecutionInputState, "W3S0XZGEM4S82", "3vqQOOnXah+v+Cwkdh/hSyDP7iD6lLGqRDW/500GvjU=")

        val executionStateOutputStateOne = tx.outputStates.first { it is ExecutionState } as ExecutionState
        val executionStateOutputStateTwo = tx.outputStates.findLast { it is ExecutionState } as ExecutionState

        val allocationState1 = tx.outputStates.get(0)
        val allocationState2 = tx.outputStates.get(1)
        val allocationState3 = tx.outputStates.get(2)
        println("allocationState ##############################")
        println(allocationState1)
        println(allocationState2)
        println(allocationState3)
        println("allocationState ##############################")

        assertNotNull(executionStateOutputStateOne)
        assertNotNull(executionStateOutputStateTwo)

        val cdmExecutionStateOutputStateOne = executionStateOutputStateOne.execution()
        val cdmExecutionStateOutputStateTwo = executionStateOutputStateTwo.execution()

        assertNotNull(cdmExecutionStateOutputStateOne)
        assertNotNull(cdmExecutionStateOutputStateTwo)
        checkIdentiferIsOnTrade(cdmExecutionStateOutputStateOne, "W3S0XZGEM4S82", "3vqQOOnXah+v+Cwkdh/hSyDP7iD6lLGqRDW/500GvjU=")
        checkIdentiferIsOnTrade(cdmExecutionStateOutputStateTwo, "ST2K6U8RHX7MZ", "3vqQOOnXah+v+Cwkdh/hSyDP7iD6lLGqRDW/500GvjU=")

        //look closer at the commands
        assertEquals(listOf(party2.owningKey, party3.owningKey), tx.commands.get(0).signers)
        assertEquals(listOf(party1.owningKey, party2.owningKey), tx.commands.get(1).signers)
        assertEquals(listOf(party1.owningKey, party2.owningKey), tx.commands.get(2).signers)

    }


}



