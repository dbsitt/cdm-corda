package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import net.corda.cdmsupport.eventparsing.parseAllocationRequestFromJson
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.functions.allocationBuilderFromExecution
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.transactionbuilding.CdmTransactionBuilder
import net.corda.cdmsupport.validators.CdmValidators

import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery

import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import java.math.BigDecimal
import java.util.function.Consumer


@InitiatingFlow
@StartableByRPC
class AllocationFlow(val allocationJson: String) : FlowLogic<SignedTransaction>() {

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

//        val evt = parseEventFromJson(allocationJson)
        val allocationRequest = parseAllocationRequestFromJson(allocationJson)
        val statesAndRef = serviceHub.vaultService.queryBy<ExecutionState>().states
        val stateAndRef = statesAndRef.first { it.state.data.execution().meta.globalKey == allocationRequest.executionRef }

        val state = stateAndRef.state.data

        val evt = allocationBuilderFromExecution(BigDecimal.valueOf(allocationRequest.amount1), BigDecimal.valueOf(allocationRequest.amount2), state)

        println(serializeCdmObjectIntoJson(evt))
        println("--------------------------------------allocation")
//        logger.info(serializeCdmObjectIntoJson(allocation))

        //get notary
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        //init query
        val query = DefaultCdmVaultQuery(serviceHub)

        //create builder
        val builder = CdmTransactionBuilder(notary,evt,query)
        builder.outputStates().forEach { System.out.println("OutputState = "+it) }
        builder.setTimeWindow(serviceHub.clock.instant(), Constant.DEFAULT_DURATION)
        //verify service hub
        builder.verify(serviceHub)

        //Init signed transaction
        val signedByMe = serviceHub.signInitialTransaction(builder)
        //Create counterparty session
        val counterPartySessions = builder.getPartiesToSign().minus(ourIdentity).map { initiateFlow(it) }
        //get fully singed transaction by other counterparties
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedByMe,counterPartySessions,CollectSignaturesFlow.tracker()))
        //finalize flow with signed transaction
        val finalityTx = subFlow(FinalityFlow(fullySignedTx,counterPartySessions))
        //find regulator node
        val regulator = serviceHub.identityService.partiesFromName("Observery",true).single()
        //create flow for regulator
        subFlow(ObserveryFlow(regulator,finalityTx))

        return finalityTx
    }
}

@InitiatedBy(AllocationFlow::class)
class AllocationFlowInitiated(val flowSession: FlowSession) : FlowLogic<SignedTransaction>(){

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
