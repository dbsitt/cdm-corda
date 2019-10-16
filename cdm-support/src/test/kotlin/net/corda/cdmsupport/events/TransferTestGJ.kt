package net.corda.cdmsupport.events

import com.derivhack.*
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoFile
import net.corda.cdmsupport.functions.allocationBuilderFromExecution
import net.corda.cdmsupport.functions.hashCDM
import net.corda.cdmsupport.states.*
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.isda.cdm.*
import org.isda.cdm.metafields.MetaFields
import org.isda.cdm.metafields.ReferenceWithMetaExecution
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransferTestGJ : BaseEventTestGJ() {

    val outputDir = TransferTestGJ::class.java.getResource("/out/").path
//    val outputDir = "C:\\Users\\CongCuong\\Downloads\\outputTest\\"
    @Test
    fun transfer() {

        val futureClientCash = node5.services.startFlow(WalletFlow(readTextFromFile("/${samplesDirectory}/UC0_money_Client_Cash_GJ.json"))).resultFuture
        futureClientCash.getOrThrow().toLedgerTransaction(node5.services)

        val futureClientSecurity = node5.services.startFlow(WalletFlow(readTextFromFile("/${samplesDirectory}/UC0_money_Client_Security_GJ.json"))).resultFuture
        futureClientSecurity.getOrThrow().toLedgerTransaction(node5.services)

        val futureBrokerCash = node5.services.startFlow(WalletFlow(readTextFromFile("/${samplesDirectory}/UC0_money_Broker_Cash_GJ.json"))).resultFuture
        futureBrokerCash.getOrThrow().toLedgerTransaction(node5.services)

        val futureBrokerSecurity = node5.services.startFlow(WalletFlow(readTextFromFile("/${samplesDirectory}/UC0_money_Broker_Security_GJ.json"))).resultFuture
        futureBrokerSecurity.getOrThrow().toLedgerTransaction(node5.services)

        val futureBroker2Cash = node5.services.startFlow(WalletFlow(readTextFromFile("/${samplesDirectory}/UC0_money_Broker_Cash_GJ_4.json"))).resultFuture
        futureBroker2Cash.getOrThrow().toLedgerTransaction(node5.services)

        val futureBroker2Security = node5.services.startFlow(WalletFlow(readTextFromFile("/${samplesDirectory}/UC0_money_Broker_Security_GJ_4.json"))).resultFuture
        futureBroker2Security.getOrThrow().toLedgerTransaction(node5.services)

        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/UC1_block_execute_BT1_GJ.json");
        val future1 = node2.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(node2.services)
        val tx1ExecutionState = tx1.outputStates.first() as ExecutionState
        val executionRef = tx1ExecutionState.execution().meta.globalKey

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
                                .setGlobalReference(executionRef)
                                .build())
                        .build())

        for (party in  tx1ExecutionState.execution().party) {
            exeEventBuilder.addParty(party.value)
        }
        val exeEvent = exeEventBuilder.setMeta(MetaFields.builder().setGlobalKey(hashCDM(exeEventBuilder.build())).build()).build()
        serializeCdmObjectIntoFile(exeEvent, "${outputDir}/uc1_out.json")

        //----------------allocation
        val jsonText2 = readTextFromFile("/${samplesDirectory}/UC2_allocation_execution_AT1_GJ.json")
        val future2 = node2.services.startFlow(AllocationFlow(jsonText2)).resultFuture
        val tx2 = future2.getOrThrow().toLedgerTransaction(node2.services)
        checkTheBasicFabricOfTheTransaction(tx2, 1, 3, 0, 3)
        val allocationExecutionKey = tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey != executionRef }.map{ it.execution().meta.globalKey}.first()

        val allocExecutionB2B =  tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey!= tx1ExecutionState.execution().meta.globalKey}
        val allocateBuilder = allocationBuilderFromExecution(allocExecutionB2B.first().execution(), allocExecutionB2B.last().execution(), tx1ExecutionState).toBuilder()
//        allocateBuilder.setEventEffect(EventEffect.builder()
//                .addEffectedExecution(ReferenceWithMetaExecution.builder().setGlobalReference(allocExecutionB2B.first().execution().meta.globalKey).build())
//                .addEffectedExecution(ReferenceWithMetaExecution.builder().setGlobalReference(allocExecutionB2B.first().execution().meta.globalKey).build())
//                .build())
        serializeCdmObjectIntoFile(allocateBuilder.build(), "${outputDir}/uc2_out.json")


        val future3 = node1.services.startFlow(AffirmationFlow(allocationExecutionKey)).resultFuture
        val tx3 = future3.getOrThrow().toLedgerTransaction(node1.services)
        val affirm = tx3.outputStates.filterIsInstance<AffirmationState>().first()
        val affirmBuilder = affirm.affirmation().toBuilder()
        affirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())

        serializeCdmObjectIntoFile(affirmBuilder.build(), "${outputDir}/uc3_out.json")

        //----------------confirmation
        val future4 = node2.services.startFlow(ConfirmationFlow(allocationExecutionKey)).resultFuture
        //val future4 = node2.services.startFlow(ConfirmationFlow(jsonText4)).resultFuture
        val tx4 = future4.getOrThrow().toLedgerTransaction(node2.services)
        val confirmationState = tx4.outputStates.filterIsInstance<ConfirmationState>().first()
        val confirmBuilder = confirmationState.confirmation().toBuilder()
        confirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())
        serializeCdmObjectIntoFile(confirmBuilder.build(), "${outputDir}/uc4_out.json")

        //------------transfer between broker to broker
        val future5 = node5.services.startFlow(SettlementFlow(executionRef)).resultFuture
        val tx5 = future5.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx5, 5, 6, 0, 1)

        val b2bInputState = tx5.inputStates.find { it is ExecutionState } as ExecutionState
        val b2bExecutionOutputState = tx5.outputStates.filterIsInstance<ExecutionState>().first()
        val b2bTransferState = tx5.outputStates.filterIsInstance<TransferState>().first()
        val b2bMoneyStates = tx5.outputStates.filterIsInstance<WalletState>()

        assertNotNull(b2bInputState)
        assertNotNull(b2bExecutionOutputState)
        assertNotNull(b2bTransferState)
        assertNotNull(b2bMoneyStates)

        assertEquals(b2bExecutionOutputState.execution().settlementTerms.meta.globalKey, b2bTransferState.transfer().settlementReference)
        //look closer at the commands
        assertTrue(tx5.commands[0].value is CDMEvent.Commands.Transfer)
        assertTrue(tx5.commands[0].signers.containsAll(listOf(party5.owningKey, party2.owningKey, party3.owningKey, party6.owningKey)))

        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(b2bTransferState.transfer(), executionRef), "${outputDir}/uc5_b2b_out.json")

//        //----------------transfer
        val future6 = node5.services.startFlow(SettlementFlow(allocationExecutionKey)).resultFuture
        val tx6 = future6.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx6, 5, 6, 0, 1)
//
        val inputState = tx6.inputStates.find { it is ExecutionState } as ExecutionState
        val outputState = tx6.outputStates.find { it is ExecutionState } as ExecutionState
        val transferState = tx6.outputStates.find { it is TransferState } as TransferState
        val moneyStates = tx6.outputStates.filterIsInstance<WalletState>()

        assertNotNull(inputState)
        assertNotNull(outputState)
        assertNotNull(transferState)

        assertEquals(outputState.execution().settlementTerms.meta.globalKey, transferState.transfer().settlementReference)

        //look closer at the commands
        assertTrue(tx6.commands[0].value is CDMEvent.Commands.Transfer)
        assertTrue(tx6.commands[0].signers.containsAll(listOf(party5.owningKey, party1.owningKey, party2.owningKey, party6.owningKey)))


        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(transferState.transfer(), allocationExecutionKey), "${outputDir}/uc5_b2c_out.json")

//        node6.services.startFlow(CollateralTopupFlow("Client1")).resultFuture.getOrThrow().toLedgerTransaction(node6.services)
//        node6.services.startFlow(CollateralTopupFlow("Client2")).resultFuture.getOrThrow().toLedgerTransaction(node6.services)
//        node6.services.startFlow(CollateralTopupFlow("Client3")).resultFuture.getOrThrow().toLedgerTransaction(node6.services)
//        node6.services.startFlow(CollateralTopupFlow("Broker1")).resultFuture.getOrThrow().toLedgerTransaction(node6.services)
//
//
//        var collateralTx = node6.services.startFlow(CollateralFlow()).resultFuture.getOrThrow().toLedgerTransaction(node6.services)
//        checkTheBasicFabricOfTheTransaction(collateralTx, 2, 2, 0, 2)
//        assertEquals(collateralTx.outputStates.filterIsInstance<CollateralWalletState>().size, 2)
//        assertEquals(collateralTx.inputStates.filterIsInstance<CollateralWalletState>().size, 2)
//        val afterSum = BigDecimal.ZERO
//        val beforeSum = BigDecimal.ZERO
//        collateralTx.outputStates.forEach { afterSum.add((it as CollateralWalletState).money().amount) }
//        collateralTx.inputStates.forEach { beforeSum.add((it as CollateralWalletState).money().amount) }
//        assertEquals(beforeSum, afterSum)
    }

    private fun createEventFromTransferPrimitive(transferPrimitive: TransferPrimitive, executionRef: String): Event {
        val exeEventBuilder = Event.builder()
                .setPrimitive(PrimitiveEvent.builder()
                        .addTransfer(transferPrimitive)
                        .build())
                .setEventEffect(EventEffect.builder()
                        .addEffectedExecution(ReferenceWithMetaExecution.builder()
                                .setGlobalReference(transferPrimitive.meta.globalKey)
                                .build())
                        .build())
                .setLineage(Lineage.builder()
                        .addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(executionRef).build())
                        .build())
//        for (party in  transferPrimitive.execution().party) {
//            exeEventBuilder.addParty(party.value)
//        }
        return exeEventBuilder.setMeta(MetaFields.builder().setGlobalKey(hashCDM(exeEventBuilder.build())).build()).build()
    }

}
