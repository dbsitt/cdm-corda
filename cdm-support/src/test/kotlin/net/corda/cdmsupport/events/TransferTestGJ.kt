package net.corda.cdmsupport.events

import com.derivhack.*
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.states.ConfirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.states.WalletState
import net.corda.cdmsupport.states.TransferState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransferTestGJ : BaseEventTestGJ() {

    private val executionRef = "xxdy/Zsa8dH/GeGisnjJhdqR8cGAuJEU2idvHFlCsuo="

    @Test
    fun transfer() {

        val jsonTextClientCash = readTextFromFile("/${samplesDirectory}/UC0_money_Client_Cash_GJ.json");
        val futureClientCash = node5.services.startFlow(WalletFlow(jsonTextClientCash)).resultFuture
        futureClientCash.getOrThrow().toLedgerTransaction(node5.services)

        val jsonTextClientSecurity = readTextFromFile("/${samplesDirectory}/UC0_money_Client_Security_GJ.json");
        val futureClientSecurity = node5.services.startFlow(WalletFlow(jsonTextClientSecurity)).resultFuture
        futureClientSecurity.getOrThrow().toLedgerTransaction(node5.services)

        val jsonTextBrokerCash = readTextFromFile("/${samplesDirectory}/UC0_money_Broker_Cash_GJ.json");
        val futureBrokerCash = node5.services.startFlow(WalletFlow(jsonTextBrokerCash)).resultFuture
        futureBrokerCash.getOrThrow().toLedgerTransaction(node5.services)

        val jsonTextBrokerSecurity = readTextFromFile("/${samplesDirectory}/UC0_money_Broker_Security_GJ.json");
        val futureBrokerSecurity = node5.services.startFlow(WalletFlow(jsonTextBrokerSecurity)).resultFuture
        futureBrokerSecurity.getOrThrow().toLedgerTransaction(node5.services)

        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/UC1_block_execute_BT1_GJ.json");
        val future1 = node2.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        future1.getOrThrow().toLedgerTransaction(node2.services)

        //----------------allocation
        val jsonText2 = readTextFromFile("/${samplesDirectory}/UC2_allocation_execution_AT1_GJ.json")
        val future2 = node2.services.startFlow(AllocationFlow(jsonText2)).resultFuture
        val tx2 = future2.getOrThrow().toLedgerTransaction(node2.services)
        checkTheBasicFabricOfTheTransaction(tx2, 1, 3, 0, 3)
        val allocationExecutionKey = tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey != executionRef }.map{ it.execution().meta.globalKey}.first()

        //val future = node1.services.startFlow(TestAffirmationFlow("vkFNMnTu1Fnk/p1gktgvz040El1XFnMwxYAVdILDlto=")).resultFuture
//        val jsonText3 = readTextFromFile("/${samplesDirectory}/UC3_affirmation_T1.1_GJ.json")
//        val affEvt = parseEventFromJson(jsonText3)
//        val affExec = affEvt.lineage.executionReference.find { it is ReferenceWithMetaExecution } as ReferenceWithMetaExecution
        //val future = node1.services.startFlow(AffirmationFlow("vkFNMnTu1Fnk/p1gktgvz040El1XFnMwxYAVdILDlto=")).resultFuture
        val future3 = node1.services.startFlow(AffirmationFlow(allocationExecutionKey)).resultFuture
        future3.getOrThrow().toLedgerTransaction(node1.services)

        //----------------confirmation
        val future4 = node2.services.startFlow(ConfirmationFlow(allocationExecutionKey)).resultFuture
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
        val future5 = node5.services.startFlow(SettlementFlow(allocationExecutionKey)).resultFuture
        val tx5 = future5.getOrThrow().toLedgerTransaction(node5.services)
        val settlementOutputState = tx5.outputStates.find { it is ExecutionState } as ExecutionState
        println("settlement ##############################")
        println(settlementOutputState.execution().party)
        println(settlementOutputState.execution().partyRole)
        println(settlementOutputState)
        println("settlement ##############################")

        //----------------transfer
        val future6 = node5.services.startFlow(TransferFlow(allocationExecutionKey)).resultFuture
        val tx6 = future6.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx6, 5, 6, 0, 1)

        val inputState = tx6.inputStates.find { it is ExecutionState } as ExecutionState
        val outputState = tx6.outputStates.find { it is ExecutionState } as ExecutionState
        val transferState = tx6.outputStates.find { it is TransferState } as TransferState
        val moneyStates = tx6.outputStates.filterIsInstance<WalletState>()

        println("after transfer moneyStates ##############################")
        println(moneyStates)
        println("after transfer moneyStates ##############################")

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
        println(outputState.participants)
        println(serializeCdmObjectIntoJson(outputState.execution()))
        println("settlementKey ##############################")

        //look closer at the commands
        assertTrue(tx6.commands.get(0).value is CDMEvent.Commands.Transfer)
        assertTrue(tx6.commands.get(0).signers.containsAll(listOf(party5.owningKey, party1.owningKey, party2.owningKey, party6.owningKey)))
    }

}
