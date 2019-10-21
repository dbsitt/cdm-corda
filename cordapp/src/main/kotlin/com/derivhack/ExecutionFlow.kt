package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import net.corda.cdmsupport.eventparsing.parseExecutionRequestFromJson
import net.corda.cdmsupport.functions.*
import net.corda.cdmsupport.transactionbuilding.CdmTransactionBuilder
import net.corda.cdmsupport.vaultquerying.DefaultCdmVaultQuery
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
@StartableByRPC
class ExecutionFlow(val jsonRequest: String) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call():SignedTransaction {

        val request = parseExecutionRequestFromJson(jsonRequest)
        val evt = createEvent(request)

        //get notary
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        //init query
        val query = DefaultCdmVaultQuery(serviceHub)

        //create builder
        val builder = CdmTransactionBuilder(notary,evt,query)
        //builder.outputStates().forEach { System.out.println("OutputState = "+it) }
        builder.setTimeWindow(serviceHub.clock.instant(), Constant.DEFAULT_DURATION)

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
