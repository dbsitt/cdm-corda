package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.functions.affirmationBuilderFromExecution
import net.corda.cdmsupport.states.AffirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.contracts.Requirements.using
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import org.isda.cdm.AffirmationStatusEnum

@InitiatingFlow
@StartableByRPC
class AffirmationFlow(val executionRef: String) : FlowLogic<SignedTransaction>() {

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
        val statesAndRef = serviceHub.vaultService.queryBy<ExecutionState>().states
        val stateAndRef = statesAndRef.first { it.state.data.execution().meta.globalKey == executionRef }

        val state = stateAndRef.state.data

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val participants = state.participants.map { net.corda.core.identity.Party(it.nameOrNull(), it.owningKey) }

        val builder = TransactionBuilder(notary)

        val affirmation = affirmationBuilderFromExecution(state)
        val affirmationState = AffirmationState(serializeCdmObjectIntoJson(affirmation), participants)
        val executionState = state.copy(workflowStatus = AffirmationStatusEnum.AFFIRMED.name)

        builder.addInputState(stateAndRef)
        builder.addCommand(CDMEvent.Commands.Affirmation(), participants.map { it.owningKey })
        builder.addOutputState(affirmationState)
        builder.addOutputState(executionState)

        builder.verify(serviceHub)

        val signedTransaction = serviceHub.signInitialTransaction(builder)

        val session = participants.minus(ourIdentity).map { initiateFlow(it) }

        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTransaction, session, CollectSignaturesFlow.tracker()))

        val regulator = serviceHub.identityService.partiesFromName("Observery", true).single()

        val finalityTx = subFlow(FinalityFlow(fullySignedTx, session))

        subFlow(ObserveryFlow(regulator,finalityTx))

        return finalityTx;

    }
}

@InitiatedBy(AffirmationFlow::class)
class AffirmationFlowInitiated(val flowSession: FlowSession) : FlowLogic<SignedTransaction>(){

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

