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

class `UC1-UC6_TEST_RUN` : BaseEventTestGJ() {

//    val outputDir = TransferTestGJ::class.java.getResource("/out/").path
    val outputDir = "/Users/admin/Downloads/testResult"
    @Test
    fun testRun() {
        testWholeFlow1("UC1_Block_Trade_BT1.json","UC2_Allocation_Trade_AT1.json")
        testWholeFlow2("UC1_Block_Trade_BT2.json","UC2_Allocation_Trade_AT2.json")
//        testWholeFlow3("UC1_Block_Trade_BT3.json","UC2_Allocation_Trade_AT3.json")
        testWholeFlow4("UC1_Block_Trade_BT4.json","UC2_Allocation_Trade_AT4.json")
//        testWholeFlow5("UC1_Block_Trade_BT5.json","UC2_Allocation_Trade_AT5.json")
        testWholeFlow6("UC1_Block_Trade_BT6.json","UC2_Allocation_Trade_AT6.json")
        testWholeFlow7("UC1_Block_Trade_BT7.json","UC2_Allocation_Trade_AT7.json")
        testWholeFlow8("UC1_Block_Trade_BT8.json","UC2_Allocation_Trade_AT8.json")
        testWholeFlow9("UC1_Block_Trade_BT9.json","UC2_Allocation_Trade_AT9.json")
        testWholeFlow10("UC1_Block_Trade_BT10.json","UC2_Allocation_Trade_AT10.json")
    }

    fun testWholeFlow1(blockFileName:String,allocationFileName:String) {
        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/${blockFileName}");
        val future1 = node2.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(node2.services)
        val tx1ExecutionState = tx1.outputStates.first() as ExecutionState
        assertEquals(tx1ExecutionState.workflowStatus, "EXECUTED")
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
        serializeCdmObjectIntoFile(exeEvent, "${outputDir}/${blockFileName.substringBeforeLast(".")}"+"_OUT.json")

        //----------------allocation
        val jsonText2 = readTextFromFile("/${samplesDirectory}/${allocationFileName}")
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
        serializeCdmObjectIntoFile(allocateBuilder.build(), "${outputDir}/${allocationFileName.substringBeforeLast(".")}"+"_OUT.json")


        val future3 = node1.services.startFlow(AffirmationFlow(allocationExecutionKey)).resultFuture
        val tx3 = future3.getOrThrow().toLedgerTransaction(node1.services)
        val affirm = tx3.outputStates.filterIsInstance<AffirmationState>().first()
        val affirmBuilder = affirm.affirmation().toBuilder()
        affirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())

        serializeCdmObjectIntoFile(affirmBuilder.build(), "${outputDir}/UC3_Affirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------confirmation
        val future4 = node2.services.startFlow(ConfirmationFlow(allocationExecutionKey)).resultFuture
        //val future4 = node2.services.startFlow(ConfirmationFlow(jsonText4)).resultFuture
        val tx4 = future4.getOrThrow().toLedgerTransaction(node2.services)
        val confirmationState = tx4.outputStates.filterIsInstance<ConfirmationState>().first()
        val confirmBuilder = confirmationState.confirmation().toBuilder()
        confirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())
        serializeCdmObjectIntoFile(confirmBuilder.build(), "${outputDir}/UC4_Confirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //------------settlement between broker to broker
        val future5 = node5.services.startFlow(SettlementFlow(executionRef)).resultFuture
        val tx5 = future5.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx5, 1, 4, 0, 1)

        val b2bInputState = tx5.inputStates.find { it is ExecutionState } as ExecutionState
        val b2bExecutionOutputState = tx5.outputStates.filterIsInstance<ExecutionState>().first()
        val b2bTransferState = tx5.outputStates.filterIsInstance<TransferState>().first()
        val b2bDBSPortfolioStates = tx5.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(b2bInputState)
        assertNotNull(b2bExecutionOutputState)
        assertNotNull(b2bTransferState)
        assertEquals(b2bDBSPortfolioStates.size, 2)

        assertEquals(b2bExecutionOutputState.execution().settlementTerms.meta.globalKey, b2bTransferState.transfer().settlementReference)
        //look closer at the commands
        assertTrue(tx5.commands[0].value is CDMEvent.Commands.Settlement)
        assertTrue(tx5.commands[0].signers.containsAll(listOf(party5.owningKey, party2.owningKey, party3.owningKey, party6.owningKey)))

        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(b2bTransferState.transfer(), executionRef), "${outputDir}/UC5_Settlement_B2B_OUT_"+allocationFileName.substringAfterLast("_"))

//        //----------------settlement between client1 and broker1
        val future6 = node5.services.startFlow(SettlementFlow(allocationExecutionKey)).resultFuture
        val tx6 = future6.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx6, 1, 4, 0, 1)
//
        val inputState = tx6.inputStates.find { it is ExecutionState } as ExecutionState
        val outputState = tx6.outputStates.find { it is ExecutionState } as ExecutionState
        val transferState = tx6.outputStates.find { it is TransferState } as TransferState
        val portfolioStates = tx6.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(inputState)
        assertNotNull(outputState)
        assertNotNull(transferState)
        assertEquals(portfolioStates.size, 2)

        assertEquals(outputState.execution().settlementTerms.meta.globalKey, transferState.transfer().settlementReference)

        //look closer at the commands
        assertTrue(tx6.commands[0].value is CDMEvent.Commands.Settlement)
        assertTrue(tx6.commands[0].signers.containsAll(listOf(party5.owningKey, party1.owningKey, party2.owningKey, party6.owningKey)))


        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(transferState.transfer(), allocationExecutionKey), "${outputDir}/UC5_Settlement_B2C_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.first().portfolio(), "${outputDir}/UC6_Portfolio_1_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.last().portfolio(), "${outputDir}/UC6_Portfolio_2_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------transfer between client1 and broker1
        val transferB2C = node5.services.startFlow(TransferFlow(allocationExecutionKey)).resultFuture
        val transferTransactionB2C = transferB2C.getOrThrow().toLedgerTransaction(node5.services)
        checkTheBasicFabricOfTheTransaction(transferTransactionB2C, 4, 4, 0, 1)
        val transferB2CExecution = transferTransactionB2C.outputStates.filterIsInstance<ExecutionState>().first()
        val transferB2CTransfer = transferTransactionB2C.outputStates.filterIsInstance<TransferState>().first()
        val transferB2CPortfolios = transferTransactionB2C.outputStates.filterIsInstance<DBSPortfolioState>()

        assertEquals(transferB2CExecution.workflowStatus, PositionStatusEnum.SETTLED.name)
        assertNotNull(transferB2CTransfer, PositionStatusEnum.SETTLED.name)
        assertEquals(transferB2CPortfolios.size, 2)
        for (portfolio in transferB2CPortfolios) {
            assertEquals(portfolio.workflowStatus, PositionStatusEnum.SETTLED.name)
            assertTrue(portfolio.participants.contains(party6))
        }
        assertTrue(transferTransactionB2C.commands[0].value is CDMEvent.Commands.Transfer)
        serializeCdmObjectIntoFile(transferB2CPortfolios.first().portfolio(), "${outputDir}/UC6_Portfolio_1_After_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(transferB2CPortfolios.last().portfolio(), "${outputDir}/UC6_Portfolio_2_After_OUT_"+allocationFileName.substringAfterLast("_"))
    }
    fun testWholeFlow2(blockFileName:String,allocationFileName:String) {
        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/${blockFileName}");
        val future1 = node3.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(node3.services)
        val tx1ExecutionState = tx1.outputStates.first() as ExecutionState
        assertEquals(tx1ExecutionState.workflowStatus, "EXECUTED")
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
        serializeCdmObjectIntoFile(exeEvent, "${outputDir}/${blockFileName.substringBeforeLast(".")}"+"_OUT.json")

        //----------------allocation
        val jsonText2 = readTextFromFile("/${samplesDirectory}/${allocationFileName}")
        val future2 = node3.services.startFlow(AllocationFlow(jsonText2)).resultFuture
        val tx2 = future2.getOrThrow().toLedgerTransaction(node3.services)
        checkTheBasicFabricOfTheTransaction(tx2, 1, 3, 0, 3)
        val allocationExecutionKey = tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey != executionRef }.map{ it.execution().meta.globalKey}.first()

        val allocExecutionB2B =  tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey!= tx1ExecutionState.execution().meta.globalKey}
        val allocateBuilder = allocationBuilderFromExecution(allocExecutionB2B.first().execution(), allocExecutionB2B.last().execution(), tx1ExecutionState).toBuilder()
//        allocateBuilder.setEventEffect(EventEffect.builder()
//                .addEffectedExecution(ReferenceWithMetaExecution.builder().setGlobalReference(allocExecutionB2B.first().execution().meta.globalKey).build())
//                .addEffectedExecution(ReferenceWithMetaExecution.builder().setGlobalReference(allocExecutionB2B.first().execution().meta.globalKey).build())
//                .build())
        serializeCdmObjectIntoFile(allocateBuilder.build(), "${outputDir}/${allocationFileName.substringBeforeLast(".")}"+"_OUT.json")


        val future3 = node7.services.startFlow(AffirmationFlow(allocationExecutionKey)).resultFuture
        val tx3 = future3.getOrThrow().toLedgerTransaction(node7.services)
        val affirm = tx3.outputStates.filterIsInstance<AffirmationState>().first()
        val affirmBuilder = affirm.affirmation().toBuilder()
        affirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())

        serializeCdmObjectIntoFile(affirmBuilder.build(), "${outputDir}/UC3_Affirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------confirmation
        val future4 = node3.services.startFlow(ConfirmationFlow(allocationExecutionKey)).resultFuture
        //val future4 = node2.services.startFlow(ConfirmationFlow(jsonText4)).resultFuture
        val tx4 = future4.getOrThrow().toLedgerTransaction(node3.services)
        val confirmationState = tx4.outputStates.filterIsInstance<ConfirmationState>().first()
        val confirmBuilder = confirmationState.confirmation().toBuilder()
        confirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())
        serializeCdmObjectIntoFile(confirmBuilder.build(), "${outputDir}/UC4_Confirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //------------settlement between broker to broker
        val future5 = node5.services.startFlow(SettlementFlow(executionRef)).resultFuture
        val tx5 = future5.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx5, 1, 4, 0, 1)

        val b2bInputState = tx5.inputStates.find { it is ExecutionState } as ExecutionState
        val b2bExecutionOutputState = tx5.outputStates.filterIsInstance<ExecutionState>().first()
        val b2bTransferState = tx5.outputStates.filterIsInstance<TransferState>().first()
        val b2bDBSPortfolioStates = tx5.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(b2bInputState)
        assertNotNull(b2bExecutionOutputState)
        assertNotNull(b2bTransferState)
        assertEquals(b2bDBSPortfolioStates.size, 2)

        assertEquals(b2bExecutionOutputState.execution().settlementTerms.meta.globalKey, b2bTransferState.transfer().settlementReference)
        //look closer at the commands
        assertTrue(tx5.commands[0].value is CDMEvent.Commands.Settlement)
        //assertTrue(tx5.commands[0].signers.containsAll(listOf(party5.owningKey, party2.owningKey, party3.owningKey, party6.owningKey)))

        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(b2bTransferState.transfer(), executionRef), "${outputDir}/UC5_Settlement_B2B_OUT_"+allocationFileName.substringAfterLast("_"))

//        //----------------settlement between client1 and broker1
        val future6 = node5.services.startFlow(SettlementFlow(allocationExecutionKey)).resultFuture
        val tx6 = future6.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx6, 1, 4, 0, 1)
//
        val inputState = tx6.inputStates.find { it is ExecutionState } as ExecutionState
        val outputState = tx6.outputStates.find { it is ExecutionState } as ExecutionState
        val transferState = tx6.outputStates.find { it is TransferState } as TransferState
        val portfolioStates = tx6.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(inputState)
        assertNotNull(outputState)
        assertNotNull(transferState)
        assertEquals(portfolioStates.size, 2)

        assertEquals(outputState.execution().settlementTerms.meta.globalKey, transferState.transfer().settlementReference)

        //look closer at the commands
        assertTrue(tx6.commands[0].value is CDMEvent.Commands.Settlement)
        //assertTrue(tx6.commands[0].signers.containsAll(listOf(party5.owningKey, party2.owningKey, party3.owningKey, party6.owningKey)))


        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(transferState.transfer(), allocationExecutionKey), "${outputDir}/UC5_Settlement_B2C_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.first().portfolio(), "${outputDir}/UC6_Portfolio_1_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.last().portfolio(), "${outputDir}/UC6_Portfolio_2_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------transfer between client1 and broker1
        val transferB2C = node5.services.startFlow(TransferFlow(allocationExecutionKey)).resultFuture
        val transferTransactionB2C = transferB2C.getOrThrow().toLedgerTransaction(node5.services)
        checkTheBasicFabricOfTheTransaction(transferTransactionB2C, 4, 4, 0, 1)
        val transferB2CExecution = transferTransactionB2C.outputStates.filterIsInstance<ExecutionState>().first()
        val transferB2CTransfer = transferTransactionB2C.outputStates.filterIsInstance<TransferState>().first()
        val transferB2CPortfolios = transferTransactionB2C.outputStates.filterIsInstance<DBSPortfolioState>()

        assertEquals(transferB2CExecution.workflowStatus, PositionStatusEnum.SETTLED.name)
        assertNotNull(transferB2CTransfer, PositionStatusEnum.SETTLED.name)
        assertEquals(transferB2CPortfolios.size, 2)
        for (portfolio in transferB2CPortfolios) {
            assertEquals(portfolio.workflowStatus, PositionStatusEnum.SETTLED.name)
            assertTrue(portfolio.participants.contains(party6))
        }
        assertTrue(transferTransactionB2C.commands[0].value is CDMEvent.Commands.Transfer)
        serializeCdmObjectIntoFile(transferB2CPortfolios.first().portfolio(), "${outputDir}/UC6_Portfolio_1_After_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(transferB2CPortfolios.last().portfolio(), "${outputDir}/UC6_Portfolio_2_After_OUT_"+allocationFileName.substringAfterLast("_"))
    }
    fun testWholeFlow4(blockFileName:String,allocationFileName:String) {
        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/${blockFileName}");
        val future1 = node2.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(node2.services)
        val tx1ExecutionState = tx1.outputStates.first() as ExecutionState
        assertEquals(tx1ExecutionState.workflowStatus, "EXECUTED")
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
        serializeCdmObjectIntoFile(exeEvent, "${outputDir}/${blockFileName.substringBeforeLast(".")}"+"_OUT.json")

        //----------------allocation
        val jsonText2 = readTextFromFile("/${samplesDirectory}/${allocationFileName}")
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
        serializeCdmObjectIntoFile(allocateBuilder.build(), "${outputDir}/${allocationFileName.substringBeforeLast(".")}"+"_OUT.json")


        val future3 = node8.services.startFlow(AffirmationFlow(allocationExecutionKey)).resultFuture
        val tx3 = future3.getOrThrow().toLedgerTransaction(node8.services)
        val affirm = tx3.outputStates.filterIsInstance<AffirmationState>().first()
        val affirmBuilder = affirm.affirmation().toBuilder()
        affirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())

        serializeCdmObjectIntoFile(affirmBuilder.build(), "${outputDir}/UC3_Affirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------confirmation
        val future4 = node2.services.startFlow(ConfirmationFlow(allocationExecutionKey)).resultFuture
        //val future4 = node2.services.startFlow(ConfirmationFlow(jsonText4)).resultFuture
        val tx4 = future4.getOrThrow().toLedgerTransaction(node2.services)
        val confirmationState = tx4.outputStates.filterIsInstance<ConfirmationState>().first()
        val confirmBuilder = confirmationState.confirmation().toBuilder()
        confirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())
        serializeCdmObjectIntoFile(confirmBuilder.build(), "${outputDir}/UC4_Confirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //------------settlement between broker to broker
        val future5 = node5.services.startFlow(SettlementFlow(executionRef)).resultFuture
        val tx5 = future5.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx5, 1, 4, 0, 1)

        val b2bInputState = tx5.inputStates.find { it is ExecutionState } as ExecutionState
        val b2bExecutionOutputState = tx5.outputStates.filterIsInstance<ExecutionState>().first()
        val b2bTransferState = tx5.outputStates.filterIsInstance<TransferState>().first()
        val b2bDBSPortfolioStates = tx5.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(b2bInputState)
        assertNotNull(b2bExecutionOutputState)
        assertNotNull(b2bTransferState)
        assertEquals(b2bDBSPortfolioStates.size, 2)

        assertEquals(b2bExecutionOutputState.execution().settlementTerms.meta.globalKey, b2bTransferState.transfer().settlementReference)
        //look closer at the commands
        assertTrue(tx5.commands[0].value is CDMEvent.Commands.Settlement)
        //assertTrue(tx5.commands[0].signers.containsAll(listOf(party5.owningKey, party2.owningKey, party3.owningKey, party6.owningKey)))

        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(b2bTransferState.transfer(), executionRef), "${outputDir}/UC5_Settlement_B2B_OUT_"+allocationFileName.substringAfterLast("_"))

//        //----------------settlement between client1 and broker1
        val future6 = node5.services.startFlow(SettlementFlow(allocationExecutionKey)).resultFuture
        val tx6 = future6.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx6, 1, 4, 0, 1)
//
        val inputState = tx6.inputStates.find { it is ExecutionState } as ExecutionState
        val outputState = tx6.outputStates.find { it is ExecutionState } as ExecutionState
        val transferState = tx6.outputStates.find { it is TransferState } as TransferState
        val portfolioStates = tx6.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(inputState)
        assertNotNull(outputState)
        assertNotNull(transferState)
        assertEquals(portfolioStates.size, 2)

        assertEquals(outputState.execution().settlementTerms.meta.globalKey, transferState.transfer().settlementReference)

        //look closer at the commands
        assertTrue(tx6.commands[0].value is CDMEvent.Commands.Settlement)
        //assertTrue(tx6.commands[0].signers.containsAll(listOf(party5.owningKey, party8.owningKey, party2.owningKey, party6.owningKey)))


        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(transferState.transfer(), allocationExecutionKey), "${outputDir}/UC5_Settlement_B2C_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.first().portfolio(), "${outputDir}/UC6_Portfolio_1_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.last().portfolio(), "${outputDir}/UC6_Portfolio_2_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------transfer between client1 and broker1
        val transferB2C = node5.services.startFlow(TransferFlow(allocationExecutionKey)).resultFuture
        val transferTransactionB2C = transferB2C.getOrThrow().toLedgerTransaction(node5.services)
        checkTheBasicFabricOfTheTransaction(transferTransactionB2C, 4, 4, 0, 1)
        val transferB2CExecution = transferTransactionB2C.outputStates.filterIsInstance<ExecutionState>().first()
        val transferB2CTransfer = transferTransactionB2C.outputStates.filterIsInstance<TransferState>().first()
        val transferB2CPortfolios = transferTransactionB2C.outputStates.filterIsInstance<DBSPortfolioState>()

        assertEquals(transferB2CExecution.workflowStatus, PositionStatusEnum.SETTLED.name)
        assertNotNull(transferB2CTransfer, PositionStatusEnum.SETTLED.name)
        assertEquals(transferB2CPortfolios.size, 2)
        for (portfolio in transferB2CPortfolios) {
            assertEquals(portfolio.workflowStatus, PositionStatusEnum.SETTLED.name)
            assertTrue(portfolio.participants.contains(party6))
        }
        assertTrue(transferTransactionB2C.commands[0].value is CDMEvent.Commands.Transfer)
        serializeCdmObjectIntoFile(transferB2CPortfolios.first().portfolio(), "${outputDir}/UC6_Portfolio_1_After_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(transferB2CPortfolios.last().portfolio(), "${outputDir}/UC6_Portfolio_2_After_OUT_"+allocationFileName.substringAfterLast("_"))
    }
    fun testWholeFlow6(blockFileName:String,allocationFileName:String) {
        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/${blockFileName}");
        val future1 = node3.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(node3.services)
        val tx1ExecutionState = tx1.outputStates.first() as ExecutionState
        assertEquals(tx1ExecutionState.workflowStatus, "EXECUTED")
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
        serializeCdmObjectIntoFile(exeEvent, "${outputDir}/${blockFileName.substringBeforeLast(".")}"+"_OUT.json")

        //----------------allocation
        val jsonText2 = readTextFromFile("/${samplesDirectory}/${allocationFileName}")
        val future2 = node3.services.startFlow(AllocationFlow(jsonText2)).resultFuture
        val tx2 = future2.getOrThrow().toLedgerTransaction(node3.services)
        checkTheBasicFabricOfTheTransaction(tx2, 1, 3, 0, 3)
        val allocationExecutionKey = tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey != executionRef }.map{ it.execution().meta.globalKey}.first()

        val allocExecutionB2B =  tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey!= tx1ExecutionState.execution().meta.globalKey}
        val allocateBuilder = allocationBuilderFromExecution(allocExecutionB2B.first().execution(), allocExecutionB2B.last().execution(), tx1ExecutionState).toBuilder()
//        allocateBuilder.setEventEffect(EventEffect.builder()
//                .addEffectedExecution(ReferenceWithMetaExecution.builder().setGlobalReference(allocExecutionB2B.first().execution().meta.globalKey).build())
//                .addEffectedExecution(ReferenceWithMetaExecution.builder().setGlobalReference(allocExecutionB2B.first().execution().meta.globalKey).build())
//                .build())
        serializeCdmObjectIntoFile(allocateBuilder.build(), "${outputDir}/${allocationFileName.substringBeforeLast(".")}"+"_OUT.json")


        val future3 = node8.services.startFlow(AffirmationFlow(allocationExecutionKey)).resultFuture
        val tx3 = future3.getOrThrow().toLedgerTransaction(node8.services)
        val affirm = tx3.outputStates.filterIsInstance<AffirmationState>().first()
        val affirmBuilder = affirm.affirmation().toBuilder()
        affirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())

        serializeCdmObjectIntoFile(affirmBuilder.build(), "${outputDir}/UC3_Affirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------confirmation
        val future4 = node3.services.startFlow(ConfirmationFlow(allocationExecutionKey)).resultFuture
        //val future4 = node2.services.startFlow(ConfirmationFlow(jsonText4)).resultFuture
        val tx4 = future4.getOrThrow().toLedgerTransaction(node3.services)
        val confirmationState = tx4.outputStates.filterIsInstance<ConfirmationState>().first()
        val confirmBuilder = confirmationState.confirmation().toBuilder()
        confirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())
        serializeCdmObjectIntoFile(confirmBuilder.build(), "${outputDir}/UC4_Confirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //------------settlement between broker to broker
        val future5 = node5.services.startFlow(SettlementFlow(executionRef)).resultFuture
        val tx5 = future5.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx5, 1, 4, 0, 1)

        val b2bInputState = tx5.inputStates.find { it is ExecutionState } as ExecutionState
        val b2bExecutionOutputState = tx5.outputStates.filterIsInstance<ExecutionState>().first()
        val b2bTransferState = tx5.outputStates.filterIsInstance<TransferState>().first()
        val b2bDBSPortfolioStates = tx5.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(b2bInputState)
        assertNotNull(b2bExecutionOutputState)
        assertNotNull(b2bTransferState)
        assertEquals(b2bDBSPortfolioStates.size, 2)

        assertEquals(b2bExecutionOutputState.execution().settlementTerms.meta.globalKey, b2bTransferState.transfer().settlementReference)
        //look closer at the commands
        assertTrue(tx5.commands[0].value is CDMEvent.Commands.Settlement)
        //assertTrue(tx5.commands[0].signers.containsAll(listOf(party5.owningKey, party2.owningKey, party3.owningKey, party6.owningKey)))

        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(b2bTransferState.transfer(), executionRef), "${outputDir}/UC5_Settlement_B2B_OUT_"+allocationFileName.substringAfterLast("_"))

//        //----------------settlement between client1 and broker1
        val future6 = node5.services.startFlow(SettlementFlow(allocationExecutionKey)).resultFuture
        val tx6 = future6.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx6, 1, 4, 0, 1)
//
        val inputState = tx6.inputStates.find { it is ExecutionState } as ExecutionState
        val outputState = tx6.outputStates.find { it is ExecutionState } as ExecutionState
        val transferState = tx6.outputStates.find { it is TransferState } as TransferState
        val portfolioStates = tx6.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(inputState)
        assertNotNull(outputState)
        assertNotNull(transferState)
        assertEquals(portfolioStates.size, 2)

        assertEquals(outputState.execution().settlementTerms.meta.globalKey, transferState.transfer().settlementReference)

        //look closer at the commands
        assertTrue(tx6.commands[0].value is CDMEvent.Commands.Settlement)
        //assertTrue(tx6.commands[0].signers.containsAll(listOf(party5.owningKey, party8.owningKey, party2.owningKey, party6.owningKey)))


        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(transferState.transfer(), allocationExecutionKey), "${outputDir}/UC5_Settlement_B2C_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.first().portfolio(), "${outputDir}/UC6_Portfolio_1_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.last().portfolio(), "${outputDir}/UC6_Portfolio_2_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------transfer between client1 and broker1
        val transferB2C = node5.services.startFlow(TransferFlow(allocationExecutionKey)).resultFuture
        val transferTransactionB2C = transferB2C.getOrThrow().toLedgerTransaction(node5.services)
        checkTheBasicFabricOfTheTransaction(transferTransactionB2C, 4, 4, 0, 1)
        val transferB2CExecution = transferTransactionB2C.outputStates.filterIsInstance<ExecutionState>().first()
        val transferB2CTransfer = transferTransactionB2C.outputStates.filterIsInstance<TransferState>().first()
        val transferB2CPortfolios = transferTransactionB2C.outputStates.filterIsInstance<DBSPortfolioState>()

        assertEquals(transferB2CExecution.workflowStatus, PositionStatusEnum.SETTLED.name)
        assertNotNull(transferB2CTransfer, PositionStatusEnum.SETTLED.name)
        assertEquals(transferB2CPortfolios.size, 2)
        for (portfolio in transferB2CPortfolios) {
            assertEquals(portfolio.workflowStatus, PositionStatusEnum.SETTLED.name)
            assertTrue(portfolio.participants.contains(party6))
        }
        assertTrue(transferTransactionB2C.commands[0].value is CDMEvent.Commands.Transfer)
        serializeCdmObjectIntoFile(transferB2CPortfolios.first().portfolio(), "${outputDir}/UC6_Portfolio_1_After_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(transferB2CPortfolios.last().portfolio(), "${outputDir}/UC6_Portfolio_2_After_OUT_"+allocationFileName.substringAfterLast("_"))
    }
    fun testWholeFlow7(blockFileName:String,allocationFileName:String) {
        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/${blockFileName}");
        val future1 = node2.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(node2.services)
        val tx1ExecutionState = tx1.outputStates.first() as ExecutionState
        assertEquals(tx1ExecutionState.workflowStatus, "EXECUTED")
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
        serializeCdmObjectIntoFile(exeEvent, "${outputDir}/${blockFileName.substringBeforeLast(".")}"+"_OUT.json")

        //----------------allocation
        val jsonText2 = readTextFromFile("/${samplesDirectory}/${allocationFileName}")
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
        serializeCdmObjectIntoFile(allocateBuilder.build(), "${outputDir}/${allocationFileName.substringBeforeLast(".")}"+"_OUT.json")


        val future3 = node8.services.startFlow(AffirmationFlow(allocationExecutionKey)).resultFuture
        val tx3 = future3.getOrThrow().toLedgerTransaction(node8.services)
        val affirm = tx3.outputStates.filterIsInstance<AffirmationState>().first()
        val affirmBuilder = affirm.affirmation().toBuilder()
        affirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())

        serializeCdmObjectIntoFile(affirmBuilder.build(), "${outputDir}/UC3_Affirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------confirmation
        val future4 = node2.services.startFlow(ConfirmationFlow(allocationExecutionKey)).resultFuture
        //val future4 = node2.services.startFlow(ConfirmationFlow(jsonText4)).resultFuture
        val tx4 = future4.getOrThrow().toLedgerTransaction(node2.services)
        val confirmationState = tx4.outputStates.filterIsInstance<ConfirmationState>().first()
        val confirmBuilder = confirmationState.confirmation().toBuilder()
        confirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())
        serializeCdmObjectIntoFile(confirmBuilder.build(), "${outputDir}/UC4_Confirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //------------settlement between broker to broker
        val future5 = node5.services.startFlow(SettlementFlow(executionRef)).resultFuture
        val tx5 = future5.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx5, 1, 4, 0, 1)

        val b2bInputState = tx5.inputStates.find { it is ExecutionState } as ExecutionState
        val b2bExecutionOutputState = tx5.outputStates.filterIsInstance<ExecutionState>().first()
        val b2bTransferState = tx5.outputStates.filterIsInstance<TransferState>().first()
        val b2bDBSPortfolioStates = tx5.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(b2bInputState)
        assertNotNull(b2bExecutionOutputState)
        assertNotNull(b2bTransferState)
        assertEquals(b2bDBSPortfolioStates.size, 2)

        assertEquals(b2bExecutionOutputState.execution().settlementTerms.meta.globalKey, b2bTransferState.transfer().settlementReference)
        //look closer at the commands
        assertTrue(tx5.commands[0].value is CDMEvent.Commands.Settlement)
        //assertTrue(tx5.commands[0].signers.containsAll(listOf(party5.owningKey, party2.owningKey, party3.owningKey, party6.owningKey)))

        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(b2bTransferState.transfer(), executionRef), "${outputDir}/UC5_Settlement_B2B_OUT_"+allocationFileName.substringAfterLast("_"))

//        //----------------settlement between client1 and broker1
        val future6 = node5.services.startFlow(SettlementFlow(allocationExecutionKey)).resultFuture
        val tx6 = future6.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx6, 1, 4, 0, 1)
//
        val inputState = tx6.inputStates.find { it is ExecutionState } as ExecutionState
        val outputState = tx6.outputStates.find { it is ExecutionState } as ExecutionState
        val transferState = tx6.outputStates.find { it is TransferState } as TransferState
        val portfolioStates = tx6.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(inputState)
        assertNotNull(outputState)
        assertNotNull(transferState)
        assertEquals(portfolioStates.size, 2)

        assertEquals(outputState.execution().settlementTerms.meta.globalKey, transferState.transfer().settlementReference)

        //look closer at the commands
        assertTrue(tx6.commands[0].value is CDMEvent.Commands.Settlement)
        //assertTrue(tx6.commands[0].signers.containsAll(listOf(party5.owningKey, party8.owningKey, party2.owningKey, party6.owningKey)))


        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(transferState.transfer(), allocationExecutionKey), "${outputDir}/UC5_Settlement_B2C_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.first().portfolio(), "${outputDir}/UC6_Portfolio_1_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.last().portfolio(), "${outputDir}/UC6_Portfolio_2_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------transfer between client1 and broker1
        val transferB2C = node5.services.startFlow(TransferFlow(allocationExecutionKey)).resultFuture
        val transferTransactionB2C = transferB2C.getOrThrow().toLedgerTransaction(node5.services)
        checkTheBasicFabricOfTheTransaction(transferTransactionB2C, 4, 4, 0, 1)
        val transferB2CExecution = transferTransactionB2C.outputStates.filterIsInstance<ExecutionState>().first()
        val transferB2CTransfer = transferTransactionB2C.outputStates.filterIsInstance<TransferState>().first()
        val transferB2CPortfolios = transferTransactionB2C.outputStates.filterIsInstance<DBSPortfolioState>()

        assertEquals(transferB2CExecution.workflowStatus, PositionStatusEnum.SETTLED.name)
        assertNotNull(transferB2CTransfer, PositionStatusEnum.SETTLED.name)
        assertEquals(transferB2CPortfolios.size, 2)
        for (portfolio in transferB2CPortfolios) {
            assertEquals(portfolio.workflowStatus, PositionStatusEnum.SETTLED.name)
            assertTrue(portfolio.participants.contains(party6))
        }
        assertTrue(transferTransactionB2C.commands[0].value is CDMEvent.Commands.Transfer)
        serializeCdmObjectIntoFile(transferB2CPortfolios.first().portfolio(), "${outputDir}/UC6_Portfolio_1_After_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(transferB2CPortfolios.last().portfolio(), "${outputDir}/UC6_Portfolio_2_After_OUT_"+allocationFileName.substringAfterLast("_"))
    }
    fun testWholeFlow8(blockFileName:String,allocationFileName:String) {
        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/${blockFileName}");
        val future1 = node3.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(node3.services)
        val tx1ExecutionState = tx1.outputStates.first() as ExecutionState
        assertEquals(tx1ExecutionState.workflowStatus, "EXECUTED")
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
        serializeCdmObjectIntoFile(exeEvent, "${outputDir}/${blockFileName.substringBeforeLast(".")}"+"_OUT.json")

        //----------------allocation
        val jsonText2 = readTextFromFile("/${samplesDirectory}/${allocationFileName}")
        val future2 = node3.services.startFlow(AllocationFlow(jsonText2)).resultFuture
        val tx2 = future2.getOrThrow().toLedgerTransaction(node3.services)
        checkTheBasicFabricOfTheTransaction(tx2, 1, 3, 0, 3)
        val allocationExecutionKey = tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey != executionRef }.map{ it.execution().meta.globalKey}.first()

        val allocExecutionB2B =  tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey!= tx1ExecutionState.execution().meta.globalKey}
        val allocateBuilder = allocationBuilderFromExecution(allocExecutionB2B.first().execution(), allocExecutionB2B.last().execution(), tx1ExecutionState).toBuilder()
//        allocateBuilder.setEventEffect(EventEffect.builder()
//                .addEffectedExecution(ReferenceWithMetaExecution.builder().setGlobalReference(allocExecutionB2B.first().execution().meta.globalKey).build())
//                .addEffectedExecution(ReferenceWithMetaExecution.builder().setGlobalReference(allocExecutionB2B.first().execution().meta.globalKey).build())
//                .build())
        serializeCdmObjectIntoFile(allocateBuilder.build(), "${outputDir}/${allocationFileName.substringBeforeLast(".")}"+"_OUT.json")


        val future3 = node1.services.startFlow(AffirmationFlow(allocationExecutionKey)).resultFuture
        val tx3 = future3.getOrThrow().toLedgerTransaction(node1.services)
        val affirm = tx3.outputStates.filterIsInstance<AffirmationState>().first()
        val affirmBuilder = affirm.affirmation().toBuilder()
        affirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())

        serializeCdmObjectIntoFile(affirmBuilder.build(), "${outputDir}/UC3_Affirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------confirmation
        val future4 = node3.services.startFlow(ConfirmationFlow(allocationExecutionKey)).resultFuture
        //val future4 = node2.services.startFlow(ConfirmationFlow(jsonText4)).resultFuture
        val tx4 = future4.getOrThrow().toLedgerTransaction(node3.services)
        val confirmationState = tx4.outputStates.filterIsInstance<ConfirmationState>().first()
        val confirmBuilder = confirmationState.confirmation().toBuilder()
        confirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())
        serializeCdmObjectIntoFile(confirmBuilder.build(), "${outputDir}/UC4_Confirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //------------settlement between broker to broker
        val future5 = node5.services.startFlow(SettlementFlow(executionRef)).resultFuture
        val tx5 = future5.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx5, 1, 4, 0, 1)

        val b2bInputState = tx5.inputStates.find { it is ExecutionState } as ExecutionState
        val b2bExecutionOutputState = tx5.outputStates.filterIsInstance<ExecutionState>().first()
        val b2bTransferState = tx5.outputStates.filterIsInstance<TransferState>().first()
        val b2bDBSPortfolioStates = tx5.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(b2bInputState)
        assertNotNull(b2bExecutionOutputState)
        assertNotNull(b2bTransferState)
        assertEquals(b2bDBSPortfolioStates.size, 2)

        assertEquals(b2bExecutionOutputState.execution().settlementTerms.meta.globalKey, b2bTransferState.transfer().settlementReference)
        //look closer at the commands
        assertTrue(tx5.commands[0].value is CDMEvent.Commands.Settlement)
        //assertTrue(tx5.commands[0].signers.containsAll(listOf(party5.owningKey, party2.owningKey, party3.owningKey, party6.owningKey)))

        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(b2bTransferState.transfer(), executionRef), "${outputDir}/UC5_Settlement_B2B_OUT_"+allocationFileName.substringAfterLast("_"))

//        //----------------settlement between client1 and broker1
        val future6 = node5.services.startFlow(SettlementFlow(allocationExecutionKey)).resultFuture
        val tx6 = future6.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx6, 1, 4, 0, 1)
//
        val inputState = tx6.inputStates.find { it is ExecutionState } as ExecutionState
        val outputState = tx6.outputStates.find { it is ExecutionState } as ExecutionState
        val transferState = tx6.outputStates.find { it is TransferState } as TransferState
        val portfolioStates = tx6.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(inputState)
        assertNotNull(outputState)
        assertNotNull(transferState)
        assertEquals(portfolioStates.size, 2)

        assertEquals(outputState.execution().settlementTerms.meta.globalKey, transferState.transfer().settlementReference)

        //look closer at the commands
        assertTrue(tx6.commands[0].value is CDMEvent.Commands.Settlement)
        //assertTrue(tx6.commands[0].signers.containsAll(listOf(party5.owningKey, party8.owningKey, party2.owningKey, party6.owningKey)))


        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(transferState.transfer(), allocationExecutionKey), "${outputDir}/UC5_Settlement_B2C_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.first().portfolio(), "${outputDir}/UC6_Portfolio_1_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.last().portfolio(), "${outputDir}/UC6_Portfolio_2_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------transfer between client1 and broker1
        val transferB2C = node5.services.startFlow(TransferFlow(allocationExecutionKey)).resultFuture
        val transferTransactionB2C = transferB2C.getOrThrow().toLedgerTransaction(node5.services)
        checkTheBasicFabricOfTheTransaction(transferTransactionB2C, 4, 4, 0, 1)
        val transferB2CExecution = transferTransactionB2C.outputStates.filterIsInstance<ExecutionState>().first()
        val transferB2CTransfer = transferTransactionB2C.outputStates.filterIsInstance<TransferState>().first()
        val transferB2CPortfolios = transferTransactionB2C.outputStates.filterIsInstance<DBSPortfolioState>()

        assertEquals(transferB2CExecution.workflowStatus, PositionStatusEnum.SETTLED.name)
        assertNotNull(transferB2CTransfer, PositionStatusEnum.SETTLED.name)
        assertEquals(transferB2CPortfolios.size, 2)
        for (portfolio in transferB2CPortfolios) {
            assertEquals(portfolio.workflowStatus, PositionStatusEnum.SETTLED.name)
            assertTrue(portfolio.participants.contains(party6))
        }
        assertTrue(transferTransactionB2C.commands[0].value is CDMEvent.Commands.Transfer)
        serializeCdmObjectIntoFile(transferB2CPortfolios.first().portfolio(), "${outputDir}/UC6_Portfolio_1_After_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(transferB2CPortfolios.last().portfolio(), "${outputDir}/UC6_Portfolio_2_After_OUT_"+allocationFileName.substringAfterLast("_"))
    }
    fun testWholeFlow9(blockFileName:String,allocationFileName:String) {
        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/${blockFileName}");
        val future1 = node3.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(node3.services)
        val tx1ExecutionState = tx1.outputStates.first() as ExecutionState
        assertEquals(tx1ExecutionState.workflowStatus, "EXECUTED")
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
        serializeCdmObjectIntoFile(exeEvent, "${outputDir}/${blockFileName.substringBeforeLast(".")}"+"_OUT.json")

        //----------------allocation
        val jsonText2 = readTextFromFile("/${samplesDirectory}/${allocationFileName}")
        val future2 = node3.services.startFlow(AllocationFlow(jsonText2)).resultFuture
        val tx2 = future2.getOrThrow().toLedgerTransaction(node3.services)
        checkTheBasicFabricOfTheTransaction(tx2, 1, 3, 0, 3)
        val allocationExecutionKey = tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey != executionRef }.map{ it.execution().meta.globalKey}.first()

        val allocExecutionB2B =  tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey!= tx1ExecutionState.execution().meta.globalKey}
        val allocateBuilder = allocationBuilderFromExecution(allocExecutionB2B.first().execution(), allocExecutionB2B.last().execution(), tx1ExecutionState).toBuilder()
//        allocateBuilder.setEventEffect(EventEffect.builder()
//                .addEffectedExecution(ReferenceWithMetaExecution.builder().setGlobalReference(allocExecutionB2B.first().execution().meta.globalKey).build())
//                .addEffectedExecution(ReferenceWithMetaExecution.builder().setGlobalReference(allocExecutionB2B.first().execution().meta.globalKey).build())
//                .build())
        serializeCdmObjectIntoFile(allocateBuilder.build(), "${outputDir}/${allocationFileName.substringBeforeLast(".")}"+"_OUT.json")


        val future3 = node7.services.startFlow(AffirmationFlow(allocationExecutionKey)).resultFuture
        val tx3 = future3.getOrThrow().toLedgerTransaction(node7.services)
        val affirm = tx3.outputStates.filterIsInstance<AffirmationState>().first()
        val affirmBuilder = affirm.affirmation().toBuilder()
        affirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())

        serializeCdmObjectIntoFile(affirmBuilder.build(), "${outputDir}/UC3_Affirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------confirmation
        val future4 = node3.services.startFlow(ConfirmationFlow(allocationExecutionKey)).resultFuture
        //val future4 = node2.services.startFlow(ConfirmationFlow(jsonText4)).resultFuture
        val tx4 = future4.getOrThrow().toLedgerTransaction(node3.services)
        val confirmationState = tx4.outputStates.filterIsInstance<ConfirmationState>().first()
        val confirmBuilder = confirmationState.confirmation().toBuilder()
        confirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())
        serializeCdmObjectIntoFile(confirmBuilder.build(), "${outputDir}/UC4_Confirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //------------settlement between broker to broker
        val future5 = node5.services.startFlow(SettlementFlow(executionRef)).resultFuture
        val tx5 = future5.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx5, 1, 4, 0, 1)

        val b2bInputState = tx5.inputStates.find { it is ExecutionState } as ExecutionState
        val b2bExecutionOutputState = tx5.outputStates.filterIsInstance<ExecutionState>().first()
        val b2bTransferState = tx5.outputStates.filterIsInstance<TransferState>().first()
        val b2bDBSPortfolioStates = tx5.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(b2bInputState)
        assertNotNull(b2bExecutionOutputState)
        assertNotNull(b2bTransferState)
        assertEquals(b2bDBSPortfolioStates.size, 2)

        assertEquals(b2bExecutionOutputState.execution().settlementTerms.meta.globalKey, b2bTransferState.transfer().settlementReference)
        //look closer at the commands
        assertTrue(tx5.commands[0].value is CDMEvent.Commands.Settlement)
        //assertTrue(tx5.commands[0].signers.containsAll(listOf(party5.owningKey, party2.owningKey, party3.owningKey, party6.owningKey)))

        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(b2bTransferState.transfer(), executionRef), "${outputDir}/UC5_Settlement_B2B_OUT_"+allocationFileName.substringAfterLast("_"))

//        //----------------settlement between client1 and broker1
        val future6 = node5.services.startFlow(SettlementFlow(allocationExecutionKey)).resultFuture
        val tx6 = future6.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx6, 1, 4, 0, 1)
//
        val inputState = tx6.inputStates.find { it is ExecutionState } as ExecutionState
        val outputState = tx6.outputStates.find { it is ExecutionState } as ExecutionState
        val transferState = tx6.outputStates.find { it is TransferState } as TransferState
        val portfolioStates = tx6.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(inputState)
        assertNotNull(outputState)
        assertNotNull(transferState)
        assertEquals(portfolioStates.size, 2)

        assertEquals(outputState.execution().settlementTerms.meta.globalKey, transferState.transfer().settlementReference)

        //look closer at the commands
        assertTrue(tx6.commands[0].value is CDMEvent.Commands.Settlement)
        //assertTrue(tx6.commands[0].signers.containsAll(listOf(party5.owningKey, party8.owningKey, party2.owningKey, party6.owningKey)))


        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(transferState.transfer(), allocationExecutionKey), "${outputDir}/UC5_Settlement_B2C_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.first().portfolio(), "${outputDir}/UC6_Portfolio_1_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.last().portfolio(), "${outputDir}/UC6_Portfolio_2_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------transfer between client1 and broker1
        val transferB2C = node5.services.startFlow(TransferFlow(allocationExecutionKey)).resultFuture
        val transferTransactionB2C = transferB2C.getOrThrow().toLedgerTransaction(node5.services)
        checkTheBasicFabricOfTheTransaction(transferTransactionB2C, 4, 4, 0, 1)
        val transferB2CExecution = transferTransactionB2C.outputStates.filterIsInstance<ExecutionState>().first()
        val transferB2CTransfer = transferTransactionB2C.outputStates.filterIsInstance<TransferState>().first()
        val transferB2CPortfolios = transferTransactionB2C.outputStates.filterIsInstance<DBSPortfolioState>()

        assertEquals(transferB2CExecution.workflowStatus, PositionStatusEnum.SETTLED.name)
        assertNotNull(transferB2CTransfer, PositionStatusEnum.SETTLED.name)
        assertEquals(transferB2CPortfolios.size, 2)
        for (portfolio in transferB2CPortfolios) {
            assertEquals(portfolio.workflowStatus, PositionStatusEnum.SETTLED.name)
            assertTrue(portfolio.participants.contains(party6))
        }
        assertTrue(transferTransactionB2C.commands[0].value is CDMEvent.Commands.Transfer)
        serializeCdmObjectIntoFile(transferB2CPortfolios.first().portfolio(), "${outputDir}/UC6_Portfolio_1_After_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(transferB2CPortfolios.last().portfolio(), "${outputDir}/UC6_Portfolio_2_After_OUT_"+allocationFileName.substringAfterLast("_"))
    }
    fun testWholeFlow10(blockFileName:String,allocationFileName:String) {
        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = readTextFromFile("/${samplesDirectory}/${blockFileName}");
        val future1 = node3.services.startFlow(ExecutionFlow(jsonText1)).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(node3.services)
        val tx1ExecutionState = tx1.outputStates.first() as ExecutionState
        assertEquals(tx1ExecutionState.workflowStatus, "EXECUTED")
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
        serializeCdmObjectIntoFile(exeEvent, "${outputDir}/${blockFileName.substringBeforeLast(".")}"+"_OUT.json")

        //----------------allocation
        val jsonText2 = readTextFromFile("/${samplesDirectory}/${allocationFileName}")
        val future2 = node3.services.startFlow(AllocationFlow(jsonText2)).resultFuture
        val tx2 = future2.getOrThrow().toLedgerTransaction(node3.services)
        checkTheBasicFabricOfTheTransaction(tx2, 1, 3, 0, 3)
        val allocationExecutionKey = tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey != executionRef }.map{ it.execution().meta.globalKey}.first()

        val allocExecutionB2B =  tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey!= tx1ExecutionState.execution().meta.globalKey}
        val allocateBuilder = allocationBuilderFromExecution(allocExecutionB2B.first().execution(), allocExecutionB2B.last().execution(), tx1ExecutionState).toBuilder()
//        allocateBuilder.setEventEffect(EventEffect.builder()
//                .addEffectedExecution(ReferenceWithMetaExecution.builder().setGlobalReference(allocExecutionB2B.first().execution().meta.globalKey).build())
//                .addEffectedExecution(ReferenceWithMetaExecution.builder().setGlobalReference(allocExecutionB2B.first().execution().meta.globalKey).build())
//                .build())
        serializeCdmObjectIntoFile(allocateBuilder.build(), "${outputDir}/${allocationFileName.substringBeforeLast(".")}"+"_OUT.json")


        val future3 = node7.services.startFlow(AffirmationFlow(allocationExecutionKey)).resultFuture
        val tx3 = future3.getOrThrow().toLedgerTransaction(node7.services)
        val affirm = tx3.outputStates.filterIsInstance<AffirmationState>().first()
        val affirmBuilder = affirm.affirmation().toBuilder()
        affirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())

        serializeCdmObjectIntoFile(affirmBuilder.build(), "${outputDir}/UC3_Affirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------confirmation
        val future4 = node3.services.startFlow(ConfirmationFlow(allocationExecutionKey)).resultFuture
        //val future4 = node2.services.startFlow(ConfirmationFlow(jsonText4)).resultFuture
        val tx4 = future4.getOrThrow().toLedgerTransaction(node3.services)
        val confirmationState = tx4.outputStates.filterIsInstance<ConfirmationState>().first()
        val confirmBuilder = confirmationState.confirmation().toBuilder()
        confirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocationExecutionKey).build()).build())
        serializeCdmObjectIntoFile(confirmBuilder.build(), "${outputDir}/UC4_Confirmation_OUT_"+allocationFileName.substringAfterLast("_"))

        //------------settlement between broker to broker
        val future5 = node5.services.startFlow(SettlementFlow(executionRef)).resultFuture
        val tx5 = future5.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx5, 1, 4, 0, 1)

        val b2bInputState = tx5.inputStates.find { it is ExecutionState } as ExecutionState
        val b2bExecutionOutputState = tx5.outputStates.filterIsInstance<ExecutionState>().first()
        val b2bTransferState = tx5.outputStates.filterIsInstance<TransferState>().first()
        val b2bDBSPortfolioStates = tx5.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(b2bInputState)
        assertNotNull(b2bExecutionOutputState)
        assertNotNull(b2bTransferState)
        assertEquals(b2bDBSPortfolioStates.size, 2)

        assertEquals(b2bExecutionOutputState.execution().settlementTerms.meta.globalKey, b2bTransferState.transfer().settlementReference)
        //look closer at the commands
        assertTrue(tx5.commands[0].value is CDMEvent.Commands.Settlement)
        //assertTrue(tx5.commands[0].signers.containsAll(listOf(party5.owningKey, party2.owningKey, party3.owningKey, party6.owningKey)))

        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(b2bTransferState.transfer(), executionRef), "${outputDir}/UC5_Settlement_B2B_OUT_"+allocationFileName.substringAfterLast("_"))

//        //----------------settlement between client1 and broker1
        val future6 = node5.services.startFlow(SettlementFlow(allocationExecutionKey)).resultFuture
        val tx6 = future6.getOrThrow().toLedgerTransaction(node5.services)

        checkTheBasicFabricOfTheTransaction(tx6, 1, 4, 0, 1)
//
        val inputState = tx6.inputStates.find { it is ExecutionState } as ExecutionState
        val outputState = tx6.outputStates.find { it is ExecutionState } as ExecutionState
        val transferState = tx6.outputStates.find { it is TransferState } as TransferState
        val portfolioStates = tx6.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(inputState)
        assertNotNull(outputState)
        assertNotNull(transferState)
        assertEquals(portfolioStates.size, 2)

        assertEquals(outputState.execution().settlementTerms.meta.globalKey, transferState.transfer().settlementReference)

        //look closer at the commands
        assertTrue(tx6.commands[0].value is CDMEvent.Commands.Settlement)
        //assertTrue(tx6.commands[0].signers.containsAll(listOf(party5.owningKey, party8.owningKey, party2.owningKey, party6.owningKey)))


        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(transferState.transfer(), allocationExecutionKey), "${outputDir}/UC5_Settlement_B2C_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.first().portfolio(), "${outputDir}/UC6_Portfolio_1_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(portfolioStates.last().portfolio(), "${outputDir}/UC6_Portfolio_2_OUT_"+allocationFileName.substringAfterLast("_"))

        //----------------transfer between client1 and broker1
        val transferB2C = node5.services.startFlow(TransferFlow(allocationExecutionKey)).resultFuture
        val transferTransactionB2C = transferB2C.getOrThrow().toLedgerTransaction(node5.services)
        checkTheBasicFabricOfTheTransaction(transferTransactionB2C, 4, 4, 0, 1)
        val transferB2CExecution = transferTransactionB2C.outputStates.filterIsInstance<ExecutionState>().first()
        val transferB2CTransfer = transferTransactionB2C.outputStates.filterIsInstance<TransferState>().first()
        val transferB2CPortfolios = transferTransactionB2C.outputStates.filterIsInstance<DBSPortfolioState>()

        assertEquals(transferB2CExecution.workflowStatus, PositionStatusEnum.SETTLED.name)
        assertNotNull(transferB2CTransfer, PositionStatusEnum.SETTLED.name)
        assertEquals(transferB2CPortfolios.size, 2)
        for (portfolio in transferB2CPortfolios) {
            assertEquals(portfolio.workflowStatus, PositionStatusEnum.SETTLED.name)
            assertTrue(portfolio.participants.contains(party6))
        }
        assertTrue(transferTransactionB2C.commands[0].value is CDMEvent.Commands.Transfer)
        serializeCdmObjectIntoFile(transferB2CPortfolios.first().portfolio(), "${outputDir}/UC6_Portfolio_1_After_OUT_"+allocationFileName.substringAfterLast("_"))
        serializeCdmObjectIntoFile(transferB2CPortfolios.last().portfolio(), "${outputDir}/UC6_Portfolio_2_After_OUT_"+allocationFileName.substringAfterLast("_"))
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
