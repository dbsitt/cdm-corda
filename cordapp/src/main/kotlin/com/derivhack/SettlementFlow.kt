package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import com.derivhack.Constant.Factory.DEFAULT_DURATION
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.functions.*
import net.corda.cdmsupport.states.DBSPortfolioState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.states.TransferState
import net.corda.cdmsupport.states.WalletState
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import org.isda.cdm.*
import org.isda.cdm.metafields.ReferenceWithMetaParty

@InitiatingFlow
@StartableByRPC
class SettlementFlow(val executionRef: String) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val moneyStates = serviceHub.vaultService.queryBy<WalletState>().states
        println("before transfer moneyStates ##############################")
        println(moneyStates)
        println("before transfer moneyStates ##############################")

        val statesAndRef = serviceHub.vaultService.queryBy<ExecutionState>().states
        val stateAndRef = statesAndRef.first { it.state.data.execution().meta.globalKey == executionRef }

        val moneyStateAndRef = serviceHub.vaultService.queryBy<WalletState>().states

        val state = stateAndRef.state.data

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val cordaCollateralAgent = serviceHub.identityService.partiesFromName(COLLATERAL_AGENT_STR, true).single()
        val collateralParticipants = mutableListOf(cordaCollateralAgent)
        collateralParticipants.addAll(state.participants
                .map { Party(it.nameOrNull(), it.owningKey) })
        val participants = state.participants.map { Party(it.nameOrNull(), it.owningKey) }

        //println("transfer participants ##############################")
        //println(participants)
        //println("transfer participants ##############################")

        val builder = TransactionBuilder(notary)
        val transferEvent = TransferBuilderFromExecution().transferBuilder(state)

        val collateralAgentRef = ReferenceWithMetaParty.builder().setGlobalReference(AgentHolder.collateralAgentParty.meta.globalKey).build()
        val executionBuilder = state.execution().toBuilder()
                .addPartyRef(AgentHolder.collateralAgentParty)
                .addPartyRole(PartyRole.builder().setRole(PartyRoleEnum.SECURED_PARTY).setPartyReference(collateralAgentRef).build())

        val executionState = state.copy(workflowStatus = TransferStatusEnum.INSTRUCTED.name, participants = collateralParticipants,  executionJson = serializeCdmObjectIntoJson(executionBuilder.build()))

        builder.addInputState(stateAndRef)
        builder.addOutputState(executionState)
        val executionRef = executionState.execution().meta.globalKey

        for (transfer in transferEvent.primitive.transfer) {
            val transferState = TransferState(serializeCdmObjectIntoJson(transfer), transferEvent.meta.globalKey, executionRef, TransferStatusEnum.INSTRUCTED.name, participants)
            builder.addOutputState(transferState)
            val portfolios = createPortfoliosFromTransfer(transfer, executionRef)
            for (portfolio in portfolios) {

                val portfolioParties : MutableSet<Party> = mutableSetOf()
                portfolio.aggregationParameters.party.forEach{
                    portfolioParties.add(serviceHub.identityService.partiesFromName(it.value.name.value, true).single())
                }

                portfolioParties.add(serviceHub.identityService.partiesFromName(SETTLEMENT_AGENT_STR, true).single())

                val dbsPortfolioState = DBSPortfolioState(serializeCdmObjectIntoJson(portfolio), PositionStatusEnum.EXECUTED.name, executionRef, portfolioParties.toList())
                builder.addOutputState(dbsPortfolioState)
            }
        }

        builder.addCommand(CDMEvent.Commands.Settlement(), collateralParticipants.map { it.owningKey })
        builder.setTimeWindow(serviceHub.clock.instant(), DEFAULT_DURATION)
        builder.verify(serviceHub)

        val signedTransaction = serviceHub.signInitialTransaction(builder)

        val session = collateralParticipants.minus(ourIdentity).map { initiateFlow(it) }

        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTransaction, session, CollectSignaturesFlow.tracker()))

        val regulator = serviceHub.identityService.partiesFromName("Observery", true).single()

        val finalityTx = subFlow(FinalityFlow(fullySignedTx, session))

        subFlow(ObserveryFlow(regulator, finalityTx))

        return finalityTx
    }
}

@InitiatedBy(SettlementFlow::class)
class SettlementFlowResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {

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


