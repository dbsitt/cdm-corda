package net.corda.cdmsupport.events

import com.derivhack.*
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.eventparsing.readEventFromJson
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.states.AffirmationState
import net.corda.cdmsupport.states.ConfirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.states.TransferState
import net.corda.cdmsupport.testflow.TestAffirmationFlow
import net.corda.cdmsupport.testflow.TestFlowInitiating
import net.corda.cdmsupport.validators.CdmValidators
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.isda.cdm.metafields.ReferenceWithMetaExecution
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransferTestGJ : BaseEventTestGJ() {

    @Test
    fun transfer() {
        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/UC1_block_execute_BT1_GJ.json");
        val future1 = node2.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        future1.getOrThrow().toLedgerTransaction(node2.services)

        //----------------allocation
        //val allocationEvent = readEventFromJson("/${samplesDirectory}/UC2_allocation_execution_AT1.json")
        val jsonText2 = readTextFromFile("/${samplesDirectory}/UC2_allocation_execution_AT1_GJ.json")
        val future2 = node2.services.startFlow(AllocationFlow(jsonText2)).resultFuture
        future2.getOrThrow().toLedgerTransaction(node2.services)
        //checkTheBasicFabricOfTheTransaction(tx2, 1, 3, 0, 3)

        //val future = node1.services.startFlow(TestAffirmationFlow("vkFNMnTu1Fnk/p1gktgvz040El1XFnMwxYAVdILDlto=")).resultFuture
        val jsonText3 = readTextFromFile("/${samplesDirectory}/UC3_affirmation_T1.1_GJ.json")
        val affEvt = parseEventFromJson(jsonText3)
        val affExec = affEvt.lineage.executionReference.find { it is ReferenceWithMetaExecution } as ReferenceWithMetaExecution
        //val future = node1.services.startFlow(AffirmationFlow("vkFNMnTu1Fnk/p1gktgvz040El1XFnMwxYAVdILDlto=")).resultFuture
        val future3 = node1.services.startFlow(AffirmationFlow(affExec.globalReference)).resultFuture
        future3.getOrThrow().toLedgerTransaction(node1.services)

        //----------------confirmation
        val jsonText4 = readTextFromFile("/${samplesDirectory}/UC4_confirmation_C1.1_GJ.json")
        val confEvt = parseEventFromJson(jsonText4)
        val confExec = confEvt.lineage.executionReference.find { it is ReferenceWithMetaExecution } as ReferenceWithMetaExecution
        val future4 = node2.services.startFlow(ConfirmationFlow(confExec.globalReference)).resultFuture
        //val future4 = node2.services.startFlow(ConfirmationFlow(jsonText4)).resultFuture
        val tx4 = future4.getOrThrow().toLedgerTransaction(node2.services)
        val confirmationState = tx4.outputStates.find { it is ConfirmationState } as ConfirmationState
        val confirmationOutputState = tx4.outputStates.find { it is ExecutionState } as ExecutionState
        println("confirmation ##############################")
        println(confirmationState.confirmation().party)
        println(confirmationState.confirmation().partyRole)
        println(confirmationState)
        println("confirmation ##############################")
        println("confirmation ##############################")
        println(confirmationOutputState.execution().party)
        println(confirmationOutputState.execution().partyRole)
        println(confirmationOutputState)
        println("confirmation ##############################")

        //----------------settlement
        val jsonText5 = readTextFromFile("/${samplesDirectory}/UC5_settlement_S1.1_GJ.json")
        val settlementEvt = parseEventFromJson(jsonText5)
        val settlementExec = settlementEvt.lineage.executionReference.find { it is ReferenceWithMetaExecution } as ReferenceWithMetaExecution
        val future5 = node5.services.startFlow(SettlementFlow(settlementExec.globalReference)).resultFuture
        val tx5 = future5.getOrThrow().toLedgerTransaction(node5.services)
        val settlementOutputState = tx5.outputStates.find { it is ExecutionState } as ExecutionState
        println("settlement ##############################")
        println(settlementOutputState.execution().party)
        println(settlementOutputState.execution().partyRole)
        println(settlementOutputState)
        println("settlement ##############################")

        //----------------transfer
        val jsonText6 = readTextFromFile("/${samplesDirectory}/UC6_tansfer_TF1.1_GJ.json")
        val transferEvt = parseEventFromJson(jsonText6)
        val transferExec = transferEvt.lineage.executionReference.find { it is ReferenceWithMetaExecution } as ReferenceWithMetaExecution
        val future6 = node5.services.startFlow(TransferFlow(transferExec.globalReference)).resultFuture
        val tx6 = future6.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx6, 1, 2, 0, 1)

        val inputState = tx6.inputStates.find { it is ExecutionState } as ExecutionState
        val outputState = tx6.outputStates.find { it is ExecutionState } as ExecutionState
        val transferState = tx6.outputStates.find { it is TransferState } as TransferState

        println("transfer input ##############################")
        println(inputState.execution().party)
        println(inputState.execution().partyRole)
        println("transfer input ##############################")

        assertNotNull(inputState)
        assertNotNull(outputState)
        assertNotNull(transferState)

        assertEquals(outputState.execution().settlementTerms.meta.globalKey, transferState.transfer().settlementReference)
        println("settlementKey ##############################")
        println(outputState.execution().settlementTerms.meta.globalKey)
        println(transferState.transfer().settlementReference)
        println("settlementKey ##############################")

        //look closer at the commands
        assertTrue(tx6.commands.get(0).value is CDMEvent.Commands.Transfer)
        assertEquals(listOf(party5.owningKey, party1.owningKey, party2.owningKey), tx6.commands.get(0).signers)
    }

}
