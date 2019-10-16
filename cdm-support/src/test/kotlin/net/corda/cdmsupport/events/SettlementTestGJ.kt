package net.corda.cdmsupport.events

import com.derivhack.*
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoFile
import net.corda.cdmsupport.functions.allocationBuilderFromExecution
import net.corda.cdmsupport.functions.hashCDM
import net.corda.cdmsupport.states.AffirmationState
import net.corda.cdmsupport.states.ConfirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.isda.cdm.*
import org.isda.cdm.metafields.MetaFields
import org.isda.cdm.metafields.ReferenceWithMetaExecution
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SettlementTestGJ : BaseEventTestGJ() {
    val outputDir = "/out/"
    @Test
    fun settlement() {
        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/UC1_block_execute_BT1_GJ.json");
        val future1 = node2.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(node2.services)
        val tx1ExecutionState = tx1.outputStates.first() as ExecutionState

        val exeEventBuilder = Event.builder()
                .setPrimitive(PrimitiveEvent.builder()
                        .addExecution(ExecutionPrimitive.builder()
                                .setAfter(org.isda.cdm.ExecutionState.builder()
                                        .setExecution(tx1ExecutionState.execution())
                                        .build())
                                .build())
                        .build())
                .setEventEffect(EventEffect.builder()
                        .addEffectedExecution(ReferenceWithMetaExecution.builder()
                                .setGlobalReference(tx1ExecutionState.execution().meta.globalKey)
                                .build())
                        .build())

        for (party in  tx1ExecutionState.execution().party) {
            exeEventBuilder.addParty(party.value)
        }
        val exeEvent = exeEventBuilder.setMeta(MetaFields.builder().setGlobalKey(hashCDM(exeEventBuilder.build())).build()).build()
        serializeCdmObjectIntoFile(exeEvent, "${outputDir}/uc1_out.json")

        //----------------allocation
        //val allocationEvent = readEventFromJson("/${samplesDirectory}/UC2_allocation_execution_AT1.json")
        val jsonText2 = readTextFromFile("/${samplesDirectory}/UC2_allocation_execution_AT1_GJ.json")
        val future2 = node2.services.startFlow(AllocationFlow(jsonText2)).resultFuture
        val tx2 = future2.getOrThrow().toLedgerTransaction(node2.services)

        val allocExecutionB2B =  tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey!= tx1ExecutionState.execution().meta.globalKey}.map { it.execution().quantity.amount }
        val amount1 = allocExecutionB2B.first()
        val amount2 = allocExecutionB2B.last()
        val alloc = allocationBuilderFromExecution(amount1, amount2, tx1ExecutionState)
        serializeCdmObjectIntoFile(alloc, "${outputDir}/uc2_out.json")
        //checkTheBasicFabricOfTheTransaction(tx2, 1, 3, 0, 3)

        //val future = node1.services.startFlow(TestAffirmationFlow("vkFNMnTu1Fnk/p1gktgvz040El1XFnMwxYAVdILDlto=")).resultFuture
        val jsonText3 = readTextFromFile("/${samplesDirectory}/UC3_affirmation_T1.1_GJ.json")
        val affEvt = parseEventFromJson(jsonText3)
        val affExec = affEvt.lineage.executionReference.find { it is ReferenceWithMetaExecution } as ReferenceWithMetaExecution
        //val future = node1.services.startFlow(AffirmationFlow("vkFNMnTu1Fnk/p1gktgvz040El1XFnMwxYAVdILDlto=")).resultFuture
        val future3 = node1.services.startFlow(AffirmationFlow(affExec.globalReference)).resultFuture
        val tx3 = future3.getOrThrow().toLedgerTransaction(node1.services)
        val affirm = tx3.outputStates.filterIsInstance<AffirmationState>().first()
        serializeCdmObjectIntoFile(affirm.affirmation(), "${outputDir}/uc3_out.json")

        //----------------confirmation
        val jsonText4 = readTextFromFile("/${samplesDirectory}/UC4_confirmation_C1.1_GJ.json")
        val confEvt = parseEventFromJson(jsonText4)
        val confExec = confEvt.lineage.executionReference.find { it is ReferenceWithMetaExecution } as ReferenceWithMetaExecution
        val future4 = node2.services.startFlow(ConfirmationFlow(confExec.globalReference)).resultFuture
        //val future4 = node2.services.startFlow(ConfirmationFlow(jsonText4)).resultFuture
        val tx4 = future4.getOrThrow().toLedgerTransaction(node2.services)
        val confirm = tx4.outputStates.filterIsInstance<ConfirmationState>().first()
        serializeCdmObjectIntoFile(confirm.confirmation(), "${outputDir}/uc4_out.json")

        val confirmationState = tx4.outputStates.find { it is ConfirmationState } as ConfirmationState
        val executionState = tx4.outputStates.find { it is ExecutionState } as ExecutionState
        println("confirmationState ##############################")
        println(confirmationState.confirmation().party)
        println(confirmationState.confirmation().partyRole)
        println(confirmationState.participants)
        println("confirmationState ##############################")

        println("executionState ##############################")
        println(executionState.execution().party)
        println(executionState.execution().partyRole)
        println(executionState.participants)
        println("executionState ##############################")

        //----------------settlement
        val jsonText5 = readTextFromFile("/${samplesDirectory}/UC5_settlement_S1.1_GJ.json")
        val settlementEvt = parseEventFromJson(jsonText5)
        val settlementExec = settlementEvt.lineage.executionReference.find { it is ReferenceWithMetaExecution } as ReferenceWithMetaExecution
        val future5 = node5.services.startFlow(SettlementFlow(settlementExec.globalReference)).resultFuture
        val tx5 = future5.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx5, 1, 1, 0, 1)

        val inputState = tx5.inputStates.find { it is ExecutionState } as ExecutionState
        val settlementState = tx5.outputStates.find { it is ExecutionState } as ExecutionState

        println("inputState ##############################")
        println(inputState.execution().party)
        println(inputState.execution().partyRole)
        println(inputState.participants)
        println("inputState ##############################")

        println("settlementState ##############################")
        println(settlementState.execution().party)
        println(settlementState.execution().partyRole)
        println(settlementState.participants)
        println("settlementState ##############################")

        assertNotNull(inputState)

        assertNotNull(settlementState.execution().settlementTerms.meta.globalKey)
        println("settlementKey ##############################")
        println(settlementState.execution().settlementTerms.meta.globalKey)
        println("settlementKey ##############################")

        //look closer at the commands
        assertTrue(tx5.commands.get(0).value is CDMEvent.Commands.Settlement)
        assertEquals(listOf(party5.owningKey, party1.owningKey, party2.owningKey), tx5.commands.get(0).signers)
    }

}
