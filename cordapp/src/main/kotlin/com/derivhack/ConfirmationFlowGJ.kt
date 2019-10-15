package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.extensions.mapPartyFromEventToCordaX500ForConfirmation
import net.corda.cdmsupport.states.ConfirmationState
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import org.isda.cdm.ConfirmationStatusEnum

@InitiatingFlow
@StartableByRPC
class ConfirmationFlowGJ(val confirmationJson: String) : FlowLogic<SignedTransaction>() {

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
        val cfmEvent = parseEventFromJson(confirmationJson)
        val executionLineage = cfmEvent.lineage.executionReference[0].globalReference
        val cdmVaultQuery = DefaultCdmVaultQuery(serviceHub)
        val inputState = cdmVaultQuery.getCdmExecutionStateByMetaGlobalKey(executionLineage)
        //val statesAndRef = serviceHub.vaultService.queryBy<ExecutionState>().states
        //val stateAndRef = statesAndRef.first { it.state.data.execution().meta.globalKey == executionRef }

        val state = inputState.state.data

        println("cfmEvent party ############################ start")
        println(cfmEvent.party)
        println("cfmEvent party ############################ end")


        //val stateExecution = state.execution()
        val participants = cfmEvent.mapPartyFromEventToCordaX500ForConfirmation(serviceHub!!)
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        //val participants = state.participants.map { net.corda.core.identity.Party(it.nameOrNull(), it.owningKey) }
        println("confirmation ############################ start")
        println(participants)
        println("confirmation ############################ end")

        val builder = TransactionBuilder(notary)

        val confirmationState = ConfirmationState(confirmationJson, participants)
        val executionState = state.copy(workflowStatus = ConfirmationStatusEnum.CONFIRMED.name, participants = participants)

        builder.addInputState(inputState)
        builder.addCommand(CDMEvent.Commands.Confirmation(), participants.map { it.owningKey })
        builder.addOutputState(confirmationState)
        builder.addOutputState(executionState)
        builder.setTimeWindow(serviceHub.clock.instant(), Constant.DEFAULT_DURATION)
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

@InitiatedBy(ConfirmationFlowGJ::class)
class ConfirmationFlowInitiated(val flowSession: FlowSession) : FlowLogic<SignedTransaction>(){

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

