package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import com.derivhack.Constant.Factory.DEFAULT_DURATION
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.functions.AgentHolder
import net.corda.cdmsupport.functions.COLLATERAL_AGENT_STR
import net.corda.cdmsupport.functions.SETTLEMENT_AGENT_STR
import net.corda.cdmsupport.functions.TransferBuilderFromExecution
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.states.TransferState
import net.corda.cdmsupport.states.WalletState
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import org.isda.cdm.PartyRole
import org.isda.cdm.PartyRoleEnum
import org.isda.cdm.metafields.ReferenceWithMetaParty

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

        val statesAndRef = serviceHub.vaultService.queryBy<ExecutionState>().states
        val stateAndRef = statesAndRef.first { it.state.data.execution().meta.globalKey == executionRef }

        val moneyStateAndRef = serviceHub.vaultService.queryBy<WalletState>().states

        val state = stateAndRef.state.data

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val cordaCollateralAgent = serviceHub.identityService.partiesFromName(COLLATERAL_AGENT_STR, true).single()
        val collateralParticipants = mutableListOf(cordaCollateralAgent)
        collateralParticipants.addAll(state.participants
                .map { net.corda.core.identity.Party(it.nameOrNull(), it.owningKey) })
        val participants = state.participants.map { net.corda.core.identity.Party(it.nameOrNull(), it.owningKey) }

        //println("transfer participants ##############################")
        //println(participants)
        //println("transfer participants ##############################")

        val builder = TransactionBuilder(notary)
        val transferEvent = TransferBuilderFromExecution().transferBuilder(state)

        val collateralAgentRef = ReferenceWithMetaParty.builder().setGlobalReference(AgentHolder.collateralAgentParty.meta.globalKey).build()
        val executionBuilder = state.execution().toBuilder()
                .addPartyRef(AgentHolder.collateralAgentParty)
                .addPartyRole(PartyRole.builder().setRole(PartyRoleEnum.SECURED_PARTY).setPartyReference(collateralAgentRef).build())

        val executionState = state.copy(workflowStatus = "TRANSFERRED", participants = collateralParticipants,  executionJson = serializeCdmObjectIntoJson(executionBuilder.build()))

        builder.addInputState(stateAndRef)
        builder.addOutputState(executionState)

        for (transfer in transferEvent.primitive.transfer) {
            val transferState = TransferState(serializeCdmObjectIntoJson(transfer), transferEvent.meta.globalKey, "TRANSFERRED", participants)
            builder.addOutputState(transferState)
            val cashPayerId = transferState.transfer().cashTransfer[0].payerReceiver.payerPartyReference.globalReference
            val cashReceiverId = transferState.transfer().cashTransfer[0].payerReceiver.receiverPartyReference.globalReference
            val cashCurrency = transferState.transfer().cashTransfer[0].amount.currency.value
            val cashAmount = transferState.transfer().cashTransfer[0].amount.amount
            val securityTransferorId = transferState.transfer().securityTransfer[0].transferorTransferee.transferorPartyReference.globalReference
            val securityTransfereeId = transferState.transfer().securityTransfer[0].transferorTransferee.transfereePartyReference.globalReference
            val securityProduct = transferState.transfer().securityTransfer[0].security.bond.productIdentifier.identifier[0].value
            val securityQuantity = transferState.transfer().securityTransfer[0].quantity

            val cashPayerWalletReference = cashPayerId + "_" + cashCurrency
            val cashReceiverWalletReference = cashReceiverId + "_" + cashCurrency
            val securityTransferorWalletReference = securityTransferorId + "_" + securityProduct
            val securityTransfereeWalletReference = securityTransfereeId + "_" + securityProduct

            val cashPayerWallet = moneyStateAndRef.first { it.state.data.walletReference == cashPayerWalletReference }
            val cashReceiverWallet = moneyStateAndRef.first { it.state.data.walletReference == cashReceiverWalletReference }
            val securityTransferorWallet = moneyStateAndRef.first { it.state.data.walletReference == securityTransferorWalletReference }
            val securityTransfereeWallet = moneyStateAndRef.first { it.state.data.walletReference == securityTransfereeWalletReference }

            val cashPayerMoneyState = cashPayerWallet.state.data
            val cashPayerNewMoney = TransferBuilderFromExecution().minusMoneyAmount(cashPayerMoneyState.money(), cashAmount)
            val cashPayerNewMoneyState = cashPayerMoneyState.copy(moneyJson = serializeCdmObjectIntoJson(cashPayerNewMoney))
            val cashReceiverMoneyState = cashReceiverWallet.state.data
            val cashReceiverNewMoney = TransferBuilderFromExecution().addMoneyAmount(cashReceiverMoneyState.money(), cashAmount)
            val cashReceiverNewMoneyState = cashReceiverMoneyState.copy(moneyJson = serializeCdmObjectIntoJson(cashReceiverNewMoney))
            val securityTransferorState = securityTransferorWallet.state.data
            val securityTransferorNewMoney = TransferBuilderFromExecution().minusMoneyAmount(securityTransferorState.money(), securityQuantity)
            val securityTransferorNewMoneyState = securityTransferorState.copy(moneyJson = serializeCdmObjectIntoJson(securityTransferorNewMoney))
            val securityTransfereeState = securityTransfereeWallet.state.data
            val securityTransfereeNewMoney = TransferBuilderFromExecution().addMoneyAmount(securityTransfereeState.money(), securityQuantity)
            val securityTransfereeNewMoneyState = cashPayerMoneyState.copy(moneyJson = serializeCdmObjectIntoJson(securityTransfereeNewMoney))

            builder.addInputState(cashPayerWallet)
            builder.addInputState(cashReceiverWallet)
            builder.addInputState(securityTransferorWallet)
            builder.addInputState(securityTransfereeWallet)

            builder.addOutputState(cashPayerNewMoneyState)
            builder.addOutputState(cashReceiverNewMoneyState)
            builder.addOutputState(securityTransferorNewMoneyState)
            builder.addOutputState(securityTransfereeNewMoneyState)
        }

        builder.addCommand(CDMEvent.Commands.Transfer(), collateralParticipants.map { it.owningKey })
        builder.setTimeWindow(serviceHub.clock.instant(), Constant.DEFAULT_DURATION)
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


