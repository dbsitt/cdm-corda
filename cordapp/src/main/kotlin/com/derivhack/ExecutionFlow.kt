package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import com.rosetta.model.lib.validation.ValidationResult
import net.corda.cdmsupport.eventparsing.parseEventFromJson
import net.corda.cdmsupport.transactionbuilding.CdmTransactionBuilder
import net.corda.cdmsupport.validators.CdmValidators
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import org.isda.cdm.Event
import org.isda.cdm.Execution
import org.isda.cdm.ExecutionState
import java.util.function.Consumer

@InitiatingFlow
@StartableByRPC
class ExecutionFlow(val executionJson: String) : FlowLogic<SignedTransaction>() {

    //TODO
    /**
     *  You're expected to convert trades from CDM representation to work towards Corda by loading
     *  the JSON file for the execution event provided for the Use Case 1 (UC1_block_execute_BT1.json),
     *  and using the parseEventFromJson function from the cdm-support package to
     *  create an Execution CDM Object and Execution State working with the CDMTransactionBuilder as well
     *  as also validate the trade against CDM data rules by using the CDMValidators.
     *
     *  Add an Observery mode to the transaction
     */


    @Suspendable
    override fun call():SignedTransaction {



        val evt = parseEventFromJson(executionJson)

        //get notary
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        //init query
        val query = DefaultCdmVaultQuery(serviceHub)

        //create builder
        val builder = CdmTransactionBuilder(notary,evt,query)
        //builder.outputStates().forEach { System.out.println("OutputState = "+it) }


        //verify service hub
        builder.verify(serviceHub)

        // validate Execution
        //val exeState = builder.outputStates().find{ it is ExecutionState } as ExecutionState
        //CdmValidators().validateExecution(exeState.execution)


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


@InitiatedBy(ExecutionFlow::class)
class ExecutionFlowInitiated(val flowSession: FlowSession) : FlowLogic<SignedTransaction>(){

    @Suspendable
    override fun call(): SignedTransaction {


        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {

                "" using ("test" is String)
            }
        }

        val signedId = subFlow(signedTransactionFlow)

        return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = signedId.id))

        //return subFlow(ReceiveFinalityFlow(flowSession,statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}
