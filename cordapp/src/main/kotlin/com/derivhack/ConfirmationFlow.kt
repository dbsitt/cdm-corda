package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.functions.AgentHolder.Factory.settlementAgentParty
import net.corda.cdmsupport.functions.SETTLEMENT_AGENT_STR
import net.corda.cdmsupport.functions.confirmationBuilderFromExecution
import net.corda.cdmsupport.functions.hashCDM
import net.corda.cdmsupport.states.ConfirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.validators.CdmValidators
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import org.isda.cdm.ConfirmationStatusEnum
import org.isda.cdm.Party
import org.isda.cdm.PartyRole
import org.isda.cdm.PartyRoleEnum
import org.isda.cdm.metafields.MetaFields
import org.isda.cdm.metafields.ReferenceWithMetaParty

@InitiatingFlow
@StartableByRPC
class ConfirmationFlow(val executionRef: String) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val statesAndRef = serviceHub.vaultService.queryBy<ExecutionState>().states
        val stateAndRef = statesAndRef.first { it.state.data.execution().meta.globalKey == executionRef }
        //println("confirmation statesAndRef ##############################")
        //println(statesAndRef)
        //println("confirmation statesAndRef ##############################")

        val state = stateAndRef.state.data
        //println("confirmation theStateAndRef ##############################")
        //println(stateAndRef)
        //println("confirmation theStateAndRef ##############################")

        //println("confirmation state ##############################")
        //println(state)
        //println("confirmation state ##############################")

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val cordaSettlementAgent = serviceHub.identityService.partiesFromName(SETTLEMENT_AGENT_STR, true).single()
        val participants = mutableListOf(cordaSettlementAgent)
        participants.addAll(state.participants
                .map { net.corda.core.identity.Party(it.nameOrNull(), it.owningKey) })

        val builder = TransactionBuilder(notary)
        val confirmation = confirmationBuilderFromExecution(state)
        CdmValidators().validateConfirmation(confirmation)
        val confirmationState = ConfirmationState(serializeCdmObjectIntoJson(confirmation), participants)
        val settlementAgentRef = ReferenceWithMetaParty.builder().setGlobalReference(settlementAgentParty.meta.globalKey).build()

        //println("settlementAgentRef ##############################")
        //println(settlementAgentRef)
        //println("settlementAgentRef ##############################")

        val executionBuilder = state.execution().toBuilder()
                .addPartyRef(settlementAgentParty)
                .addPartyRole(PartyRole.builder().setRole(PartyRoleEnum.SETTLEMENT_AGENT).setPartyReference(settlementAgentRef).build())

        val executionState = state.copy(workflowStatus = ConfirmationStatusEnum.CONFIRMED.name, participants = participants,  executionJson = serializeCdmObjectIntoJson(executionBuilder.build()))

        //println("confirmation executionState ##############################")
        //println(executionState)
        //println("confirmation executionState ##############################")

        builder.addInputState(stateAndRef)
        builder.addCommand(CDMEvent.Commands.Confirmation(), participants.map { it.owningKey })
        builder.addOutputState(confirmationState)
        builder.addOutputState(executionState)

        builder.verify(serviceHub)

        val signedTransaction = serviceHub.signInitialTransaction(builder)

        val session = participants.minus(ourIdentity).map { initiateFlow(it) }

        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTransaction, session, CollectSignaturesFlow.tracker()))

        val regulator = serviceHub.identityService.partiesFromName("Observery", true).single()

        val finalityTx = subFlow(FinalityFlow(fullySignedTx, session))

        subFlow(ObserveryFlow(regulator, finalityTx))

        return finalityTx
    }
}

@InitiatedBy(ConfirmationFlow::class)
class ConfirmationFlowResponse(val flowSession: FlowSession): FlowLogic<SignedTransaction>() {
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