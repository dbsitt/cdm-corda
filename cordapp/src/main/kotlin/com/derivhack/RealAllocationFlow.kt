package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.functions.allocationBuilderFromExecution
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.transactionbuilding.CdmTransactionBuilder
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import java.math.BigDecimal


@InitiatingFlow
@StartableByRPC
class RealAllocationFlow(val executionRef: String) : FlowLogic<SignedTransaction>() {

    //TODO
    /**
     *  You're expected to work towards the JSON file for the allocation event provided for the
     *  Use Case 2 (UC2_allocation_execution_AT1.json), by using the parseEventFromJson function
     *  from the cdm-support package and ingest/consume the allocation trades on Corda,
     *  demonstrate lineage to the block trade from Use Case 1 and validate the trade
     *  against CDM data rules by creating validations similar to those for Use Case 1.
     *
     *  Add an Observery mode to the transaction
     */

    @Suspendable
    override fun call(): SignedTransaction {
        val statesAndRef = serviceHub.vaultService.queryBy<ExecutionState>().states
        val stateAndRef = statesAndRef.first { it.state.data.execution().meta.globalKey == executionRef }

        val state = stateAndRef.state.data

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val allocation = allocationBuilderFromExecution(BigDecimal.valueOf(320000), BigDecimal.valueOf(480000), state)
        logger.info(serializeCdmObjectIntoJson(allocation))
        val cdmTransactionBuilder = CdmTransactionBuilder(notary, allocation, DefaultCdmVaultQuery(serviceHub))
        cdmTransactionBuilder.verify(serviceHub)
//        cdmTransactionBuilder.setTimeWindow(Timewindow)
        val signedByMe = serviceHub.signInitialTransaction(cdmTransactionBuilder)

        val counterPartySessions = cdmTransactionBuilder.getPartiesToSign().minus(ourIdentity).map { initiateFlow(it) }

        val regulator = serviceHub.identityService.partiesFromName("Observery", true).single()

        val fullySignedTx = subFlow(CollectSignaturesFlow(signedByMe, counterPartySessions, CollectSignaturesFlow.tracker()))
        val finalityTx = subFlow(FinalityFlow(fullySignedTx, counterPartySessions))
        subFlow(ObserveryFlow(regulator, finalityTx))

        return finalityTx

    }
}

@InitiatedBy(RealAllocationFlow::class)
class RealAllocationFlowInitiated(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {

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
