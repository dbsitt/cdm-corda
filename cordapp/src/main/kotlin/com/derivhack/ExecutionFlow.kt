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
class ExecutionFlow(val evt: Event) : FlowLogic<SignedTransaction>() {

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


        println("################ Hurrayy inside ExecutionFlow-call().......................")
//        val evt = parseEventFromJson(executionJson)

        //get notary
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        //init query
        val query = DefaultCdmVaultQuery(serviceHub)

        //create builder
        val builder = CdmTransactionBuilder(notary,evt,query)
        builder.outputStates().forEach { System.out.println("OutputState = "+it) }

        println("#########################@ExecutionFlow.call() ----commented out the builder.verify()............")
        //verify service hub
//        builder.verify(serviceHub)

        // validate Execution
        //val exeState = builder.outputStates().find{ it is ExecutionState } as ExecutionState
        //CdmValidators().validateExecution(exeState.execution)


        //Init signed transaction
        println("#########################@ExecutionFlow.call() ---- serviceHub.signInitialTransaction()............")
        val signedByMe = serviceHub.signInitialTransaction(builder)
        println("#########################@ExecutionFlow.call() ---- After serviceHub.signInitialTransaction()............")

        //Create counterparty session
        println("#########################@ExecutionFlow.call() ----builder.getPartiesToSign().minus()............")
        val counterPartySessions = builder.getPartiesToSign().minus(ourIdentity).map { initiateFlow(it) }
        println("#########################@ExecutionFlow.call() ---- After builder.getPartiesToSign().minus()............")

        //get fully singed transaction by other counterparties
        println("#########################@ExecutionFlow.call() ----subFlow(CollectSignaturesFlow()............")
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedByMe,counterPartySessions,CollectSignaturesFlow.tracker()))
        println("#########################@ExecutionFlow.call() ---- After subFlow(CollectSignaturesFlow()............")

        //finalize flow with signed transaction
        println("#########################@ExecutionFlow.call() ----subFlow(FinalityFlow()............")
        val finalityTx = subFlow(FinalityFlow(fullySignedTx,counterPartySessions))
        println("#########################@ExecutionFlow.call() ---- After subFlow(FinalityFlow()............")

        //find regulator node
        println("#########################@ExecutionFlow.call() ----serviceHub.identityService.partiesFromName()............")
        val regulator = serviceHub.identityService.partiesFromName("Observery",true).single()
        println("#########################@ExecutionFlow.call() ---- After serviceHub.identityService.partiesFromName()............")

        //create flow for regulator
        println("#########################@ExecutionFlow.call() ----subFlow(ObserveryFlow()............")
        subFlow(ObserveryFlow(regulator,finalityTx))
        println("#########################@ExecutionFlow.call() ----After subFlow(ObserveryFlow()............")

        println("################################### Finished up the ExecutionFlow by returning finalityTx...............")
        return finalityTx

        //val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
        //val exe = rosettaObjectMapper.readValue<org.isda.cdm.Execution>(executionJson, org.isda.cdm.Execution::class.java)

        /*
        val exeStt:ExecutionState = ExecutionState(executionJson,"","",ArrayList<Party>());

        val exe = exeStt.execution()
        exe.createExecutionWithPartiesFromEvent(evt)

        //val exe:Execution = Execution.builder().build().createExecutionWithPartiesFromEvent(evt);

        val validators = CdmValidators()
        validators.validateExecution(exe)



        val outputIndex = transactionBuilder.addOutputStateReturnIndex(exeStt,CDMEvent.ID)
        val exeCmd = CDMEvent.Commands.Execution(outputIndex)
        transactionBuilder.addCommand(exeCmd)

        val signedTrans = serviceHub.signInitialTransaction(transactionBuilder)
        subFlow(ObserveryFlow(ourIdentity,signedTrans))
        */
    }
}

@InitiatedBy(ExecutionFlow::class)
class ExecutionFlowInitiated(val flowSession: FlowSession) : FlowLogic<SignedTransaction>(){

    @Suspendable
    override fun call(): SignedTransaction {

        println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> start")
        val signedTransactionFlow = object : SignTransactionFlow(flowSession) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {

                "" using ("test" is String)
            }
        }

        val signedId = subFlow(signedTransactionFlow)
        println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> end")
        return subFlow(ReceiveFinalityFlow(otherSideSession = flowSession, expectedTxId = signedId.id))

        //return subFlow(ReceiveFinalityFlow(flowSession,statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}
