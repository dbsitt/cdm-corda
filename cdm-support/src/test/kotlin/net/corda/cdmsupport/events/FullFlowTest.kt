package net.corda.cdmsupport.events

import com.derivhack.*
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.CollateralInstructions
import net.corda.cdmsupport.eventparsing.parseCorllateralInstructionWrapperFromJson
import net.corda.cdmsupport.eventparsing.readEventFromJson
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoFile
import net.corda.cdmsupport.functions.allocationBuilderFromExecution
import net.corda.cdmsupport.functions.hashCDM
import net.corda.cdmsupport.states.*
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.startFlow
import org.isda.cdm.*
import org.isda.cdm.metafields.*
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FullFlowTest : BaseEventTestGJ() {

    private val outputDir = TransferTestGJ::class.java.getResource("/out/").path
    var runCase67 = true

    @Test
    fun testRun() {
        val nameNodeMap = mapOf(
                "Client1" to node1,
                "Broker1" to node2,
                "Broker2" to node3,
                "Observery" to node4,
                "SettlementAgent" to node5,
                "CollateralAgent" to node6,
                "Client2" to node7,
                "Client3" to node8
        )

        val failTest = mutableMapOf<Int, String>()
        for (i in 1..10) {
            try {
                val executionFile = "UC1_Block_Trade_BT$i.json"
                val event = readEventFromJson("/${samplesDirectory}/UC1_Block_Trade_BT$i.json")
                val parties = event.party
                check(parties.size == 3) { "Should have 3 party here" }
                val client = nameNodeMap[parties[0].name.value]!!
                val executingEntity = nameNodeMap[parties[1].name.value]!!
                val counterParty = nameNodeMap[parties[2].name.value]!!
                testWholeFlow(executionFile, "UC2_Allocation_Trade_AT$i.json", client, executingEntity, counterParty)
            } catch (ex: Exception) {
                failTest.put(i, ex.message.toString())
            }
        }

        for (entry in failTest) {
            println( "Test case " + entry.key + " fail with reason: " + entry.value)
        }
    }

    private fun testWholeFlow(blockTradeFile: String, allocationFile: String, client: TestStartedNode, executingEntity: TestStartedNode, counterParty: TestStartedNode) {
        val executionState = testExecution(blockTradeFile, executingEntity)
        val executionRef = executionState.execution().meta.globalKey
        val version = allocationFile.substringAfterLast("_").split(".").first()
        val allocRefs = testAllocation(executionState, allocationFile, executingEntity)
        var subVersion = 1
        for (allocRef in allocRefs) {
            testAffirmation(allocRef, client, "$version.$subVersion.json")
            testConfirmation(allocRef, executingEntity, "$version.$subVersion.json")
            testSettlementAndTransfer(allocRef, node5, "$version.$subVersion.json")
            subVersion++
        }
        testSettlementAndTransfer(executionRef, node5, "$version.$subVersion.json")
    }

    private fun testExecution(blockFileName: String, executingEntity: TestStartedNode): ExecutionState {
        val jsonText1 = readTextFromFile("/${samplesDirectory}/${blockFileName}")
        val future1 = executingEntity.services.startFlow(ExecutionFlowJson(jsonText1)).resultFuture
        val tx1 = future1.getOrThrow().toLedgerTransaction(executingEntity.services)
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
        return tx1ExecutionState
    }

    private fun testAllocation(tx1ExecutionState: ExecutionState, allocationFile: String, executingEntity: TestStartedNode): List<String> {
        val executionRef = tx1ExecutionState.execution().meta.globalKey
        val jsonText2 = readTextFromFile("/${samplesDirectory}/${allocationFile}")
        val future2 = executingEntity.services.startFlow(AllocationFlowJson(jsonText2)).resultFuture
        val tx2 = future2.getOrThrow().toLedgerTransaction(executingEntity.services)
        checkTheBasicFabricOfTheTransaction(tx2, 1, 3, 0, 3)
        val allocExecutionB2B =  tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey!= executionRef}
        val allocateBuilder = allocationBuilderFromExecution(allocExecutionB2B.first().execution(), allocExecutionB2B.last().execution(), tx1ExecutionState).toBuilder()
        serializeCdmObjectIntoFile(allocateBuilder.build(), "${outputDir}/${allocationFile.substringBeforeLast(".")}"+"_OUT.json")
        return tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey != executionRef }.map{ it.execution().meta.globalKey}
    }

    private fun testAffirmation(allocRef: String, client: TestStartedNode, version: String) {
        val future3 = client.services.startFlow(AffirmationFlow(allocRef)).resultFuture
        val tx3 = future3.getOrThrow().toLedgerTransaction(client.services)
        val affirm = tx3.outputStates.filterIsInstance<AffirmationState>().first()
        val affirmBuilder = affirm.affirmation().toBuilder()
        affirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocRef).build()).build())

        serializeCdmObjectIntoFile(affirmBuilder.build(), "${outputDir}/UC3_Affirmation_OUT_"+version)
    }

    private fun testConfirmation(allocRef: String, executingEntity: TestStartedNode, version: String) {
        val future4 = executingEntity.services.startFlow(ConfirmationFlow(allocRef)).resultFuture
        val tx4 = future4.getOrThrow().toLedgerTransaction(executingEntity.services)
        val confirmationState = tx4.outputStates.filterIsInstance<ConfirmationState>().first()
        val confirmBuilder = confirmationState.confirmation().toBuilder()
        confirmBuilder.setLineage(Lineage.builder().addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(allocRef).build()).build())
        serializeCdmObjectIntoFile(confirmBuilder.build(), "${outputDir}/UC4_Confirmation_OUT_"+version)
    }

    private fun testSettlementAndTransfer(executionRef: String, settlementAgent: TestStartedNode, version: String) {
        val future5 = settlementAgent.services.startFlow(SettlementFlow(executionRef)).resultFuture
        val tx5 = future5.getOrThrow().toLedgerTransaction(settlementAgent.services)

        checkTheBasicFabricOfTheTransaction(tx5, 1, 4, 0, 1)

        val b2bInputState = tx5.inputStates.find { it is ExecutionState } as ExecutionState
        val b2bExecutionOutputState = tx5.outputStates.filterIsInstance<ExecutionState>().first()
        val b2bTransferState = tx5.outputStates.filterIsInstance<TransferState>().first()
        val portfolioStates = tx5.outputStates.filterIsInstance<DBSPortfolioState>()

        assertNotNull(b2bInputState)
        assertNotNull(b2bExecutionOutputState)
        assertNotNull(b2bTransferState)
        assertEquals(portfolioStates.size, 2)

        assertEquals(b2bExecutionOutputState.execution().settlementTerms.meta.globalKey, b2bTransferState.transfer().settlementReference)
        //look closer at the commands
        assertTrue(tx5.commands[0].value is CDMEvent.Commands.Settlement)
//        assertTrue(tx5.commands[0].signers.containsAll(listOf(party5.owningKey, party2.owningKey, party3.owningKey, party6.owningKey)))
        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(b2bTransferState.transfer(), executionRef), "${outputDir}/UC5_Settlement_OUT_" + version)

        if (runCase67) {
//            Do case 6 and 7 here
            serializeCdmObjectIntoFile(portfolioStates.last().portfolio(), "${outputDir}/UC6_Client_Portfolio_Report_20191016.json")
            //----------------transfer between client1 and broker1
            val transferB2C = settlementAgent.services.startFlow(TransferFlow(executionRef)).resultFuture
            val transferTransactionB2C = transferB2C.getOrThrow().toLedgerTransaction(settlementAgent.services)
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
            serializeCdmObjectIntoFile(transferB2CPortfolios.last().portfolio(), "${outputDir}/UC6_Client_Portfolio_Report_20191017.json")

            val instructedJson = readTextFromFile("/${samplesDirectory}/UC7_Collateral_Instructions.json")
            val instruction = parseCorllateralInstructionWrapperFromJson(instructedJson)

            val client1SubAcc = instruction.collateralInstructions.client.partyId
            val client1SegregatedAcc = instruction.collateralInstructions.clientSegregated.partyId

            val uc7Future = node6.services.startFlow(CollateralFlow(instructedJson)).resultFuture
            val uc7Transaction = uc7Future.getOrThrow().toLedgerTransaction(node6.services)
            val uc7Portfolios = uc7Transaction.outputStates.filterIsInstance<DBSPortfolioState>()
            checkTheBasicFabricOfTheTransaction(uc7Transaction, 0, 3, 0, 1)
            val party1Portfolio = mutableListOf<DBSPortfolioState>()
            party1Portfolio.addAll(transferB2CPortfolios.filter { it.portfolio().aggregationParameters.party.first().value.partyId.first().value == client1SubAcc.first().value })
            party1Portfolio.addAll(uc7Portfolios.filter { it.portfolio().aggregationParameters.party.first().value.partyId.first().value == client1SubAcc.first().value })

            val party1SegregatedPortfolio = mutableListOf<DBSPortfolioState>()
            party1SegregatedPortfolio.addAll(transferB2CPortfolios.filter { it.portfolio().aggregationParameters.party.first().value.partyId.first().value == client1SegregatedAcc.first().value })
            party1SegregatedPortfolio.addAll(uc7Portfolios.filter { it.portfolio().aggregationParameters.party.first().value.partyId.first().value == client1SegregatedAcc.first().value })

            val transferEvent = createTransferEventFromInstruction(instruction.collateralInstructions)
            serializeCdmObjectIntoFile(generateFinalPortfolio(party1Portfolio,transferEvent), "${outputDir}/UC7_Client_Portfolio_After.json")
            serializeCdmObjectIntoFile(transferEvent, "${outputDir}/UC7_Collateral_Event.json")
            serializeCdmObjectIntoFile(generateFinalPortfolio(party1SegregatedPortfolio, transferEvent), "${outputDir}/UC7_Segregated_Portfolio_After.json")
            runCase67 = false
        }
    }

    private fun generateFinalPortfolio(clientPortfolioStates: List<DBSPortfolioState>, event: Event): Portfolio {
        var balance = BigDecimal.ZERO

        for (portfolioState in clientPortfolioStates) {
            balance = balance.add(portfolioState.portfolio().portfolioState.positions.first().quantity.amount)
        }
        val product = clientPortfolioStates.first().portfolio().portfolioState.positions.first().product
        val builder = clientPortfolioStates.first().portfolio().toBuilder()
        builder.portfolioState.lineage.clearEventReference().clearExecutionReference().clearTransferReference()
        builder.portfolioState.lineage
                .addEventReference(ReferenceWithMetaEvent.builder().setGlobalReference(event.meta.globalKey).build())
                .addTransferReference(ReferenceWithMetaTransferPrimitive.builder().setGlobalReference(event.primitive.transfer.first().meta.globalKey).build())
        builder.portfolioState.clearPositions()
        builder.portfolioState.addPositions(Position.builder()
                .setQuantity(Quantity.builder().setAmount(balance).build())
                .setProduct(product)
                .build())
        return builder.build()
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

    private fun createTransferEventFromInstruction(instruction: CollateralInstructions): Event {
        val transferBuilder = TransferPrimitive.builder()
                .addSecurityTransfer(SecurityTransferComponent.builder()
                        .setTransferorTransferee(TransferorTransferee.builder()
                                .setTransferorPartyReference(ReferenceWithMetaParty.builder().setValue(instruction.client).build())
                                .setTransfereePartyReference(ReferenceWithMetaParty.builder().setValue(instruction.clientSegregated).build())
                                .build())
                        .setQuantity(instruction.netIM.quantity.amount)
                        .setSecurity(instruction.security)
                        .build())
        val transfer = transferBuilder.setMeta(MetaFields.builder().setGlobalKey(hashCDM(transferBuilder.build())).build()).build()
        val eventBuilder = Event.builder()
                .setEventEffect(EventEffect.builder()
                        .addTransfer(ReferenceWithMetaTransferPrimitive.builder().setGlobalReference(transfer.meta.globalKey).build())
                        .build())
                .addEventIdentifier(Identifier.builder()
//                        .setIssuerReference(ReferenceWithMetaParty.builder()
//                                .setGlobalReference(instruction.client.meta.globalKey)
//                                .build())
                        .addAssignedIdentifier(AssignedIdentifier.builder()
                                .setIdentifier(FieldWithMetaString.builder()
                                        .setValue("NZVJ31U4568YT")
                                        .build())
                                .build())
                        .build())
                .setPrimitive(PrimitiveEvent.builder()
                        .addTransfer(transfer)
                        .build())

        return eventBuilder.setMeta(MetaFields.builder().setGlobalKey(hashCDM(eventBuilder.build())).build()).build()
    }

}
