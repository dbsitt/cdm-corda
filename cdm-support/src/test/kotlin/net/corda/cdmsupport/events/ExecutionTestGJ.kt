package net.corda.cdmsupport.events

import com.derivhack.ExecutionFlow
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.junit.Test

class ExecutionTestGJ : BaseEventTestGJ() {

    @Test
    fun execution() {
        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/UC1_block_execute_BT1_GJ.json");
        val future1 = node2.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(node2.services)
        val executionOutputState = tx1.outputStates.find { it is ExecutionState } as ExecutionState
        println("executionOutputState ##############################")
        println(executionOutputState.execution().party)
        println(executionOutputState.execution().partyRole)
        println(executionOutputState)
        println("executionOutputState ##############################")
    }
}
