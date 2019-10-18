package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.CollateralInstructions
import net.corda.cdmsupport.NetIM
import net.corda.cdmsupport.eventparsing.parseCorllateralInstructionWrapperFromJson
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.functions.COLLATERAL_AGENT_STR
import net.corda.cdmsupport.functions.hashCDM
import net.corda.cdmsupport.states.DBSPortfolioState
import net.corda.cdmsupport.states.TransferState
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import org.isda.cdm.*
import org.isda.cdm.metafields.*
import java.math.BigDecimal

@InitiatingFlow
@StartableByRPC
class CollateralFlow(private val instructionJson: String) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val builder = TransactionBuilder(notary)
        val instruction = parseCorllateralInstructionWrapperFromJson(instructionJson).collateralInstructions
        val payerOutput = createPortfolioStateFromClientAndProduct(instruction.client, instruction.netIM, instruction.security, true)
        val receiverOutput = createPortfolioStateFromClientAndProduct(instruction.clientSegregated, instruction.netIM, instruction.security, false)
        builder.addOutputState(payerOutput)
        builder.addOutputState(receiverOutput)

        val transferEvent = createTransferEventFromInstruction(instruction)
        val allParticipants = mutableSetOf<Party>()
        allParticipants.addAll(payerOutput.participants)
        allParticipants.addAll(receiverOutput.participants)
        builder.addOutputState(TransferState(serializeCdmObjectIntoJson(transferEvent.primitive.transfer.first()), transferEvent.meta.globalKey, "dummyEventRef", TransferStatusEnum.SETTLED.name, allParticipants.toList()))

        builder.addCommand(CDMEvent.Commands.Collateral(), allParticipants.map { it.owningKey })
        builder.setTimeWindow(serviceHub.clock.instant(), Constant.DEFAULT_DURATION)
        builder.verify(serviceHub)

        val signedTransaction = serviceHub.signInitialTransaction(builder)
        val session = allParticipants.minus(ourIdentity).map { initiateFlow(it) }

        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTransaction, session, CollectSignaturesFlow.tracker()))

        val regulator = serviceHub.identityService.partiesFromName("Observery", true).single()

        val finalityTx = subFlow(FinalityFlow(fullySignedTx, session))

        subFlow(ObserveryFlow(regulator,finalityTx))

        return finalityTx;
    }

    fun createTransferEventFromInstruction(instruction: CollateralInstructions): Event {
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

    private fun createPortfolioStateFromClientAndProduct(client: org.isda.cdm.Party, netIM: NetIM, security: Security, isPayer: Boolean) : DBSPortfolioState {
        val amount = if(isPayer) netIM.quantity.amount.negate() else netIM.quantity.amount
        val participants = mutableSetOf(serviceHub.identityService.partiesFromName(COLLATERAL_AGENT_STR, true).single(),
                serviceHub.identityService.partiesFromName(client.name.value, true).single())
        val portfolioBuilder = Portfolio.builder()
                .setPortfolioState(PortfolioState.builder()
                        .addPositions(Position.builder()
                                .setQuantity(Quantity.builder().setAmount(amount).build())
                                .setProduct(Product.builder().setSecurity(security).build())
                                .setCashBalance(Money.builder()
                                        .setAmount(BigDecimal.ZERO)
                                        .setCurrency(FieldWithMetaString.builder().setValue("USD").build())
                                        .build())
                                .setPositionStatus(PositionStatusEnum.SETTLED)
                                .build()).build())
                .setAggregationParameters(AggregationParameters.builder()
                        .addParty(ReferenceWithMetaParty.builder().setValue(client).build())
                        .build())
        val hash = hashCDM(portfolioBuilder.build())
        portfolioBuilder.portfolioState.setLineage(Lineage.builder()
                .addTransferReference(ReferenceWithMetaTransferPrimitive.builder().setGlobalReference(hash).build())
                .addExecutionReference(ReferenceWithMetaExecution.builder().setGlobalReference(hash).build())
                .build())
        return DBSPortfolioState(serializeCdmObjectIntoJson(portfolioBuilder.build()), PositionStatusEnum.SETTLED.name, hash, participants.toList())
    }
}

@InitiatedBy(CollateralFlow::class)
class CollateralFlowResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>(){

    @Suspendable
    override fun call(): SignedTransaction {
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {

                "" using ("test" is String)
            }
        }
        val signedId = subFlow(signedTransactionFlow)
        return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = signedId.id))
    }
}