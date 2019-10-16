package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import com.derivhack.Constant.Factory.DEFAULT_DURATION
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.functions.COLLATERAL_AGENT_STR
import net.corda.cdmsupport.states.DBSPortfolioState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.states.TransferState
import net.corda.cdmsupport.states.WalletState
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import org.isda.cdm.PositionStatusEnum
import org.isda.cdm.metafields.ReferenceWithMetaTransferPrimitive

@InitiatingFlow
@StartableByRPC
class TransferFlow(val executionRef: String) : FlowLogic<SignedTransaction>() {

    //TODO
    /**
     *  You're expected to generate relevant CDM objects and link them to associated allocated
     *  trades created with Use Case 2 as well as validate them against CDM data rules by
     *  creating validations similar to those for the previous use cases
     *
     *  Add an Observery mode to the transaction
     */

    @Suspendable
    override fun call(): SignedTransaction {
        val moneyStates = serviceHub.vaultService.queryBy<WalletState>().states
        println("before transfer moneyStates ##############################")
        println(moneyStates)
        println("before transfer moneyStates ##############################")

        val executionStateAndRef = serviceHub.vaultService.queryBy<ExecutionState>().states.first { it.state.data.execution().meta.globalKey == executionRef }
        val transferStateAndRef = serviceHub.vaultService.queryBy<TransferState>().states.first { it.state.data.executionReference == executionRef }
        val portfolioStatesAndRef = serviceHub.vaultService.queryBy<DBSPortfolioState>().states.filter { it.state.data.executionRef == executionRef }
        check(portfolioStatesAndRef.size == 2) {"Should have 2 portfolio state here"}


        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val cordaCollateralAgent = serviceHub.identityService.partiesFromName(COLLATERAL_AGENT_STR, true).single()
        val collateralParticipants = mutableListOf(cordaCollateralAgent)
        collateralParticipants.addAll(transferStateAndRef.state.data.participants
                .map { net.corda.core.identity.Party(it.nameOrNull(), it.owningKey) })
        val participants = collateralParticipants.map { net.corda.core.identity.Party(it.nameOrNull(), it.owningKey) }

        //println("transfer participants ##############################")
        //println(participants)
        //println("transfer participants ##############################")

        val builder = TransactionBuilder(notary)
        builder.addInputState(executionStateAndRef)
        builder.addInputState(transferStateAndRef)
        for (portfolioStateAndRef in portfolioStatesAndRef) {
            builder.addInputState(portfolioStateAndRef)
            val portfolio = portfolioStateAndRef.state.data.portfolio()
            val portfolioBuilder = portfolio.toBuilder()
            portfolioBuilder.portfolioState.positions.first().setPositionStatus(PositionStatusEnum.SETTLED)
            portfolioBuilder.portfolioState.lineage.clearExecutionReference()
                    .addTransferReference(ReferenceWithMetaTransferPrimitive.builder().setGlobalReference(transferStateAndRef.state.data.transfer().meta.globalKey)
                    .build())

            val dbsPortfolioOutputState = portfolioStateAndRef.state.data.copy(portfolioJson = serializeCdmObjectIntoJson(portfolioBuilder.build()), workflowStatus = PositionStatusEnum.SETTLED.name)
            builder.addOutputState(dbsPortfolioOutputState)
        }

        builder.addOutputState(executionStateAndRef.state.data.copy(workflowStatus = PositionStatusEnum.SETTLED.name))
        builder.addOutputState(transferStateAndRef.state.data.copy(workflowStatus = PositionStatusEnum.SETTLED.name))


        builder.addCommand(CDMEvent.Commands.Transfer(), collateralParticipants.map { it.owningKey })
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

@InitiatedBy(TransferFlow::class)
class TransferResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {

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


