package net.corda.cdmsupport

import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction

class CDMEvent : Contract {


    companion object {
        val ID = "net.corda.cdmsupport.CDMEvent"
    }

    interface Commands : CommandData {
        class Affirmation() : Commands
        class Execution(val outputIndex: Int) : Commands
    }

    override fun verify(tx: LedgerTransaction) {
        // TODO: Write the verify logic.
        //val command = tx.commands.requireSingleCommand<Commands>();

        /*
        requireThat {
            val output = tx.outputsOfType<LinearState>().single();
            val expectedSigners = output.participants.map { party -> party.owningKey  };
            "There must be signers." using (command.signers.containsAll(expectedSigners))
        }
        */

    }


}











