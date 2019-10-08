package com.derivhack

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

@InitiatingFlow
@StartableByRPC
class ObserveryFlow(private val regulator: Party, private val finalTx: SignedTransaction) : FlowLogic<Unit>() {

    //TODO
    /**
     * You're expected to create an Observery for each transaction
     */

    @Suspendable
    override fun call() {
        val session = initiateFlow(regulator)
        //subFlow(SendTransactionFlow(session, finalTx))
        session.send(finalTx)
    }
}
@InitiatedBy(ObserveryFlow::class)
class ReceiveObservableFlow(private val counterpartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        //subFlow(ReceiveTransactionFlow(otherSideSession, false, StatesToRecord.ALL_VISIBLE))
        val signedTransaction = counterpartySession.receive<SignedTransaction>().unwrap { it }
        serviceHub.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(signedTransaction))
    }
}


