package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.NetIM
import net.corda.cdmsupport.eventparsing.parseCorllateralInstructionWrapperFromJson
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.functions.COLLATERAL_AGENT_STR
import net.corda.cdmsupport.states.DBSPortfolioState
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import org.isda.cdm.*
import org.isda.cdm.metafields.ReferenceWithMetaParty

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
        val allParticipants = mutableSetOf<Party>()
        allParticipants.addAll(payerOutput.participants)
        allParticipants.addAll(receiverOutput.participants)
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

    private fun createPortfolioStateFromClientAndProduct(client: org.isda.cdm.Party, netIM: NetIM, security: Security, isPayer: Boolean) : DBSPortfolioState {
        val amount = if(isPayer) netIM.quantity.amount.negate() else netIM.quantity.amount
        val participants = mutableSetOf(serviceHub.identityService.partiesFromName(COLLATERAL_AGENT_STR, true).single(),
                serviceHub.identityService.partiesFromName(client.name.value, true).single())
        val portfolioBuilder = Portfolio.builder()
                .setPortfolioState(PortfolioState.builder()
                        .addPositions(Position.builder()
                                .setQuantity(Quantity.builder().setAmount(amount).build())
                                .setProduct(Product.builder().setSecurity(security).build())
                                .build()).build())
                .setAggregationParameters(AggregationParameters.builder()
                        .addParty(ReferenceWithMetaParty.builder().setValue(client).build())
                        .build())

        return DBSPortfolioState(serializeCdmObjectIntoJson(portfolioBuilder.build()), "COLLATERAL", "---", participants.toList())
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