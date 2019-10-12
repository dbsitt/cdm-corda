package net.corda.cdmsupport

import com.r3.corda.finance.obligation.contracts.commands.ObligationCommands
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
        println("############### here at the StatesAndContracts @CDMEvent->verify() #############################")
        println("###### @CDMEvent->verify() tx.commands ${tx.commands} #############")
        // TODO: Write the verify logic.
        val command = tx.commands.requireSingleCommand<Commands>();

        requireThat {
            val output = tx.outputsOfType<LinearState>().single();
            val expectedSigners = output.participants.map { party -> party.owningKey  };
            "There must be signers." using (command.signers.containsAll(expectedSigners))
        }

        println("/////////////////////////////////////////////////////////////////////////////////////////////////////////////////// - Start");
        tx.commands?.forEach{
            System.out.println("command - "+it)
            tx.outputStates?.forEach{
                println(">>>>>>>>>>>>>>>>>> outoutStates:")
                System.out.println("output - "+it)
            }
            tx.inputStates?.forEach{
                println(">>>>>>>>>>>>>>>>>> inputStates:")
                System.out.println("input - "+it)
            }
            tx.referenceStates?.forEach{
                println(">>>>>>>>>>>>>>>>>> referenceStates:")
                System.out.println("ref - "+it)
            }
        }
        println("/////////////////////////////////////////////////////////////////////////////////////////////////////////////////// - End");

    }


}











