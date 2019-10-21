package net.corda.cdmsupport.events

import com.derivhack.*
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.CollateralInstructions
import net.corda.cdmsupport.eventparsing.parseCorllateralInstructionWrapperFromJson
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoFile
import net.corda.cdmsupport.functions.allocationBuilderFromExecution
import net.corda.cdmsupport.functions.hashCDM
import net.corda.cdmsupport.states.*
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.internal.startFlow
import org.isda.cdm.*
import org.isda.cdm.metafields.*
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TransferTestGJ : BaseEventTestGJ() {

//        val outputDir = TransferTestGJ::class.java.getResource("/out/").path
    val outputDir = "C:\\Users\\CongCuong\\Downloads\\outputTest\\"

    @Test
    fun transfer() {
        //sendNewTradeInAndCheckAssertionsGJ("UC1_block_execute_BT1_GJ.json")
        val jsonText1 = """
            {
              "client" : "Client1",
              "executingEntity": "Broker1",
              "counterParty": "Broker2",
              "buySell": "buy",
              "product": "DH0371475458",
              "price": 99.88,
              "quantity": 800000,
              "tradeDate": "2019/10/16",
              "eventDate": "2019/10/16"
            }
            """.trimIndent()
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

        for (party in tx1ExecutionState.execution().party) {
            exeEventBuilder.addParty(party.value)
        }
        val exeEvent = exeEventBuilder.setMeta(MetaFields.builder().setGlobalKey(hashCDM(exeEventBuilder.build())).build()).build()
        serializeCdmObjectIntoFile(exeEvent, "${outputDir}/uc1_out.json")

        //----------------allocation
        val jsonText2 = "{\"executionRef\": \"$executionRef\",\"amount1\": 320000,\"amount2\": 480000}"
        val future2 = node2.services.startFlow(AllocationFlow(jsonText2)).resultFuture
        val tx2 = future2.getOrThrow().toLedgerTransaction(node2.services)
        checkTheBasicFabricOfTheTransaction(tx2, 1, 3, 0, 3)
        val allocationExecutionKey = tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey != executionRef }.map { it.execution().meta.globalKey }.first()

        val allocExecutionB2B = tx2.outputStates.filterIsInstance<ExecutionState>().filter { it.execution().meta.globalKey != tx1ExecutionState.execution().meta.globalKey }
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

        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(b2bTransferState.transfer(), executionRef), "${outputDir}/uc5_b2b_out.json")

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


        serializeCdmObjectIntoFile(createEventFromTransferPrimitive(transferState.transfer(), allocationExecutionKey), "${outputDir}/uc5_b2c_out.json")
        serializeCdmObjectIntoFile(portfolioStates.first().portfolio(), "${outputDir}/uc6_b2c_portfolio1.json")
        serializeCdmObjectIntoFile(portfolioStates.last().portfolio(), "${outputDir}/uc6_b2c_portfolio2.json")

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
        serializeCdmObjectIntoFile(transferB2CPortfolios.first().portfolio(), "${outputDir}/uc6_b2c_portfolio1_after.json")
        serializeCdmObjectIntoFile(transferB2CPortfolios.last().portfolio(), "${outputDir}/uc6_b2c_portfolio2_after.json")

        val instructedJson = readTextFromFile("/${samplesDirectory}/UC7_Collateral_Instructions.json")
        val instruction = parseCorllateralInstructionWrapperFromJson(instructedJson)

        var client1SubAcc = instruction.collateralInstructions.client.partyId
        var client1SegregatedAcc = instruction.collateralInstructions.clientSegregated.partyId

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
