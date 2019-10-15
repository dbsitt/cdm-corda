package net.corda.cdmsupport.events

import com.derivhack.AffirmationFlow
import com.derivhack.RealAllocationFlow
import com.derivhack.ExecutionFlow
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.states.AffirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.validators.CdmValidators
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.isda.cdm.metafields.ReferenceWithMetaExecution
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AffirmationTestGJ : BaseEventTestGJ() {

    @Test
    fun affirmation() {
        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/UC1_block_execute_BT1_GJ.json");
        val future1 = node2.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        future1.getOrThrow().toLedgerTransaction(node2.services)

        //----------------allocation
        //val allocationEvent = readEventFromJson("/${samplesDirectory}/UC2_allocation_execution_AT1.json")
        val jsonText2 = readTextFromFile("/${samplesDirectory}/UC2_allocation_execution_AT1_GJ.json")
        val future2 = node2.services.startFlow(RealAllocationFlow(jsonText2)).resultFuture
        future2.getOrThrow().toLedgerTransaction(node2.services)
        //checkTheBasicFabricOfTheTransaction(tx2, 1, 3, 0, 3)

        //val future = node1.services.startFlow(TestAffirmationFlow("vkFNMnTu1Fnk/p1gktgvz040El1XFnMwxYAVdILDlto=")).resultFuture
        val jsonText3 = readTextFromFile("/${samplesDirectory}/UC3_affirmation_T1.1_GJ.json")
        val affEvt = parseEventFromJson(jsonText3)
        val affExec = affEvt.lineage.executionReference.find { it is ReferenceWithMetaExecution} as ReferenceWithMetaExecution

        //val future = node1.services.startFlow(AffirmationFlow("vkFNMnTu1Fnk/p1gktgvz040El1XFnMwxYAVdILDlto=")).resultFuture
        val future3 = node1.services.startFlow(AffirmationFlow(affExec.globalReference)).resultFuture
        val tx = future3.getOrThrow().toLedgerTransaction(node1.services)

        checkTheBasicFabricOfTheTransaction(tx, 1, 2, 0, 1)

        val inputState = tx.inputStates.find { it is ExecutionState } as ExecutionState

        val affirmationState = tx.outputStates.find { it is AffirmationState } as AffirmationState
        val executionState = tx.outputStates.find { it is ExecutionState } as ExecutionState

        CdmValidators().validateAffirmation(affirmationState.affirmation())

        assertNotNull(inputState)
        assertNotNull(affirmationState)
        assertNotNull(executionState)

        //look closer at the commands
        assertTrue(tx.commands.get(0).value is CDMEvent.Commands.Affirmation)
        assertEquals(listOf(party1.owningKey, party2.owningKey), tx.commands.get(0).signers)
    }

}
