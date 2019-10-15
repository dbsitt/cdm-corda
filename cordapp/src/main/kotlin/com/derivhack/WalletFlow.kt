package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.functions.AgentHolder.Factory.settlementAgentParty
import net.corda.cdmsupport.functions.MoneyBuilderFromJson
import net.corda.cdmsupport.states.WalletState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class WalletFlow(val moneyJson: String) : FlowLogic<SignedTransaction>() {

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
        // create CDM::Money with new Money global key
        // use party json global key to find existing party, if not found, create new party
        // create MoneyState, get from existing MoneyState, check party and ccy, if existing, then inputState = existing
        // if not existing, inputState = null

        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val inputMoney = MoneyBuilderFromJson().moneyBuilder(moneyJson)
        val inputParty = MoneyBuilderFromJson().partyBuilder(moneyJson)
        val walletReference = inputParty.meta.globalKey + "_" + inputMoney.currency.value
        val newMoneyJson = serializeCdmObjectIntoJson(inputMoney)
        val newPartyJson = serializeCdmObjectIntoJson(inputParty)

        val parties : MutableSet<Party> = mutableSetOf()
        parties.add(serviceHub.identityService
                .wellKnownPartyFromX500Name(CordaX500Name.parse("O=${inputParty.name.value},L=New York,C=US"))!!)
        parties.add(serviceHub.identityService
                .wellKnownPartyFromX500Name(CordaX500Name.parse("O=${settlementAgentParty.name.value},L=New York,C=US"))!!)
        val participants = parties.toList()

        val moneyState = WalletState (newMoneyJson, newPartyJson, walletReference, inputParty.meta.globalKey, inputParty.name.value, inputParty.partyId[0].value, participants, UniqueIdentifier())

        val builder = TransactionBuilder(notary)
        builder.addOutputState(moneyState)
        builder.addCommand(CDMEvent.Commands.Money(), participants.map { it.owningKey })

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

@InitiatedBy(WalletFlow::class)
class WalletResponder(val flowSession: FlowSession) : FlowLogic<SignedTransaction>() {

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


