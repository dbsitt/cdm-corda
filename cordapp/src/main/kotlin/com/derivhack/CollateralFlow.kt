package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import com.derivhack.Constant.Factory.DEFAULT_COLLATERAL_RATE
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.functions.extractParty
import net.corda.cdmsupport.states.CollateralWalletState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import org.isda.cdm.PartyRoleEnum
import java.math.BigDecimal

@InitiatingFlow
@StartableByRPC
class CollateralFlow() : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val statesAndRef = serviceHub.vaultService.queryBy<CollateralWalletState>().states
        val brokerStateAndRef = statesAndRef.first{it.state.data.party().name.value.contains("Broker")}

        val transactionStatesAndRef = serviceHub.vaultService.queryBy<ExecutionState>().states
        val allParticipants = mutableSetOf<Party>()
        var sum = BigDecimal.ZERO
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        val builder = TransactionBuilder(notary)
        for (transactionStateAndRef in transactionStatesAndRef) {
            val client = extractParty(transactionStateAndRef.state.data, PartyRoleEnum.CLIENT).value.name
            val clientStateAndRef = statesAndRef.first { it.state.data.party().name.value == client.value }
            builder.addInputState(clientStateAndRef)
            val amount = getAmount(transactionStateAndRef.state.data).negate()
            sum = sum.add(amount)
            val participants = clientStateAndRef.state.data.participants.map { Party(it.nameOrNull(), it.owningKey) }
            println("${client.value} - $amount")
            allParticipants.addAll(participants)
            builder.addCommand(CDMEvent.Commands.Collateral(), participants.map { it.owningKey })
            builder.addOutputState(calculate(clientStateAndRef.state.data, amount))
        }

//      add for broker
        builder.addInputState(brokerStateAndRef)
        val participants = brokerStateAndRef.state.data.participants.map { Party(it.nameOrNull(), it.owningKey) }
        allParticipants.addAll(participants)
        builder.addCommand(CDMEvent.Commands.Collateral(), participants.map { it.owningKey })
        val brokerAmount = sum.negate()
        println("Broker Amount: $brokerAmount")
        builder.addOutputState(calculate(brokerStateAndRef.state.data, brokerAmount))

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

    private fun getAmount(executionState: ExecutionState) : BigDecimal {
        return executionState.execution().settlementTerms.settlementAmount.amount.multiply(BigDecimal.valueOf(DEFAULT_COLLATERAL_RATE))
    }

    private fun calculate(collateralWalletState: CollateralWalletState, amount: BigDecimal): CollateralWalletState {
        val newBalance = collateralWalletState.money().amount.add(amount.minus(collateralWalletState.lastTransaction))
        println("Calculating: " + collateralWalletState.ownerPartyName
                + ", last transaction: " + collateralWalletState.lastTransaction
                + ", this transaction: " + amount
                + ", current balance: " + collateralWalletState.money().amount
                + ", new balance: " + newBalance)
        val money = collateralWalletState.money().toBuilder().setAmount(newBalance).build()
        return collateralWalletState.copy(moneyJson = serializeCdmObjectIntoJson(money), lastTransaction = amount)
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