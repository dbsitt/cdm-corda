package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.eventparsing.readEventFromJson
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.functions.AgentHolder
import net.corda.cdmsupport.functions.COLLATERAL_AGENT_STR
import net.corda.cdmsupport.functions.SETTLEMENT_AGENT_STR
import net.corda.cdmsupport.states.WalletState
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import org.isda.cdm.*
import org.isda.cdm.metafields.FieldWithMetaString
import java.math.BigDecimal

@InitiatingFlow
@StartableByRPC
class InitFlow(val executionRef: String) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        val builder = TransactionBuilder(notary)
        for (collateralAccount in listOf("Client1", "Client2", "Client3", "Broker1", "Broker2")) {
            subFlow(CollateralTopupFlow(collateralAccount))
        }
        val data = "C:\\Users\\CongCuong\\Downloads\\Pre Event Data-Published\\UC2_Allocation_Trade_AT"
        val sumSecurity = mutableMapOf<String, BigDecimal>()
        val parties = mutableMapOf<String, Party>()
        val partyProducts = mutableMapOf<String, MutableSet<String>>()
        for (i in 1..10) {
            val event = readEventFromJson(data + "${i}.json")
            val exec = event.primitive.allocation.first().before
            val bondId = exec.execution.product.security.bond.productIdentifier.identifier.first().value
            sumSecurity.putIfAbsent(bondId, BigDecimal.ZERO)
            sumSecurity.putIfAbsent("USD", BigDecimal.ZERO)
            sumSecurity[bondId] = sumSecurity[bondId]!!.add(exec.execution.quantity.amount)
            sumSecurity["USD"] = sumSecurity["USD"]!!.add(exec.execution.settlementTerms.settlementAmount.amount)
            for( trade in event.primitive.allocation.first().after.allocatedTrade) {
                val party = extractParty(event, trade, PartyRoleEnum.CLIENT)
                val partyId = party.partyId.first().value
                parties.put(partyId, party)
                partyProducts.putIfAbsent(partyId, mutableSetOf())
                partyProducts[partyId]!!.addAll(extractProducts(trade))
            }
        }

        val settlementAgent = serviceHub.identityService
                .wellKnownPartyFromX500Name(CordaX500Name.parse("O=${AgentHolder.settlementAgentParty.name.value},L=New York,C=US"))!!
        val collateralAgent = serviceHub.identityService
                .wellKnownPartyFromX500Name(CordaX500Name.parse("O=${AgentHolder.collateralAgentParty.name.value},L=New York,C=US"))!!
        val allParticipants = mutableSetOf(settlementAgent, collateralAgent)
        for (partyProd in partyProducts) {
            val party = parties[partyProd.key]!!
            val partyJson = serializeCdmObjectIntoJson(party)

            for (product in partyProd.value) {
                val prodJson = serializeCdmObjectIntoJson(Money.builder().setAmount(sumSecurity[product]).setCurrency(FieldWithMetaString.builder().setValue(product).build()).build())
                val cordaParty = serviceHub.identityService
                        .wellKnownPartyFromX500Name(CordaX500Name.parse("O=${party.name.value},L=New York,C=US"))!!
                val walletReference = party.meta.globalKey + "_" + product
                builder.addOutputState(WalletState(prodJson, partyJson, walletReference, party.meta.globalKey, party.name.value, party.partyId.first().value, listOf(cordaParty, settlementAgent)))

            }
            builder.addCommand(CDMEvent.Commands.WalletTopup(), allParticipants.filter { it.name.organisation in listOf(COLLATERAL_AGENT_STR, SETTLEMENT_AGENT_STR, party.name.value) }.map { it.owningKey })
        }
        builder.verify(serviceHub)

        val signedTransaction = serviceHub.signInitialTransaction(builder)

        val session = allParticipants.minus(ourIdentity).map { initiateFlow(it) }

        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTransaction, session, CollectSignaturesFlow.tracker()))

        val regulator = serviceHub.identityService.partiesFromName("Observery", true).single()

        val finalityTx = subFlow(FinalityFlow(fullySignedTx, session))

        subFlow(ObserveryFlow(regulator, finalityTx))
        return finalityTx

    }

    private fun extractParty(event: Event, trade: Trade, role: PartyRoleEnum) : Party {
        return event.party.first{it.meta.globalKey == trade.execution.partyRole.first{it.role == role}.partyReference.globalReference }
    }

    private fun extractProducts(trade: Trade): List<String> {
        return listOf(trade.execution.price.netPrice.currency.value, trade.execution.product.security.bond.productIdentifier.identifier.first().value)
    }
}

@InitiatedBy(InitFlow::class)
class InitFlowResponder(val flowSession: FlowSession): FlowLogic<SignedTransaction>() {
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