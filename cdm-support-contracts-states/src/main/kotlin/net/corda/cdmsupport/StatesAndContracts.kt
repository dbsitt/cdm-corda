package net.corda.cdmsupport

import net.corda.cdmsupport.states.AffirmationState
import net.corda.cdmsupport.states.ConfirmationState
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.contracts.*
import net.corda.core.contracts.Requirements.using
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.loggerFor
import org.isda.cdm.ConfirmationStatusEnum
import java.security.PublicKey

class CDMEvent : Contract {


    companion object {
        val ID = "net.corda.cdmsupport.CDMEvent"
    }

    interface Commands : CommandData {
        class Affirmation() : Commands
        class Confirmation() : Commands
        class Settlement() : Commands
        class Transfer() : Commands
        class WalletTopup(): Commands
        class Collateral(): Commands
        class Execution(val outputIndex: Int) : Commands
        class Allocation(val outputIndex: Int) : Commands
    }

    override fun verify(tx: LedgerTransaction) {
        processCommands(tx,tx.commands)
    }

    private fun processCommands(tx: LedgerTransaction,commands:List<CommandWithParties<CommandData>>){

        val logger = loggerFor<StateAndContract>()

        commands.forEach {
            logger.debug("print command - $it")
            //basic check
            //every transaction should have output state
            "Output states must be defined." using ( tx.outputStates.isNotEmpty() )

            val command = it.value
            val signers = it.signers.toSet()
            when(command){
                is Commands.Execution -> verifyExecutionCommand(command,tx,signers)
                is Commands.Affirmation -> verifyAffirmationCommand(command,tx,signers)
                is Commands.Confirmation -> verifyConfirmationCommand(command,tx,signers)
            }
        }
    }


    private fun verifyExecutionCommand(execution: Commands.Execution,tx: LedgerTransaction,signers: Set<PublicKey>) = requireThat {


        val logger = loggerFor<StateAndContract>()
        logger.debug("------ Execution outputIndex: "+ execution.outputIndex)
        logger.debug("------ InputStates Count: "+ tx.inputStates.size)
        logger.debug("------ OutputStates Count: "+ tx.outputStates.size)
        logger.debug("------ RefStates Count: "+ tx.referenceStates.size)

        //try to check correct parties are signing the transaction
        if(tx.outputStates.size == 1) {
            tx.outputsOfType<ExecutionState>().forEach {
                "Both parties together only may sign issue transaction." using (signers == keysFromParticipants(it))
            }
        }

        //if multiple output states found, it means this is "allocation" related transaction
        //it requires to check input states in transactions
        if(tx.outputStates.size > 1){
            "Input states must be defined when multiple output states were defined in transaction." using tx.inputStates?.isNotEmpty()

            /*
            var quantityAmtBefore = BigDecimal.ZERO;
            var allocatedTotals = BigDecimal.ZERO


            val exeOutputStates = tx.outputsOfType<ExecutionState>()
            quantityAmtBefore = quantityAmtBefore.plus(exeOutputStates.first().execution().quantity.amount)

            exeOutputStates.subList(1,tx.outputStates.size).map {
                allocatedTotals = allocatedTotals.plus(
                it.execution().quantity.amount)
            }

            logger.debug("------ qtBefore:$quantityAmtBefore")
            logger.debug("------ alTotal:$allocatedTotals")

            "Ensure sum of allocation is equal to block trade." using (quantityAmtBefore.compareTo(allocatedTotals).equals(0))

             */
        }

    }


    private fun verifyAffirmationCommand(execution: Commands.Affirmation,tx: LedgerTransaction,signers: Set<PublicKey>) = requireThat {
        val logger = loggerFor<StateAndContract>()
        val inputState = tx.inputStates.find { it is ExecutionState } as ExecutionState
        val affirmationState = tx.outputStates.find { it is AffirmationState } as AffirmationState
        "Both parties together only may sign issue transaction." using (signers == keysFromParticipants(affirmationState))
        val executionState = tx.outputStates.find { it is ExecutionState } as ExecutionState
        "Both parties together only may sign issue transaction." using (signers == keysFromParticipants(executionState))

        logger.debug("------ Input state execution: "+ inputState.execution())
        logger.debug("------ Output state affirm: "+ affirmationState.affirmation())
        logger.debug("------ Output state execution: "+ executionState.execution())

        logger.debug("------ Input state execution status: "+ inputState.workflowStatus)
        logger.debug("------ Output state affirm status: "+ affirmationState.affirmation().status)
        logger.debug("------ Output state execution status: "+ executionState.workflowStatus)

        "Status of out execution state and affirmation state are same." using (affirmationState.affirmation().status.name == executionState.workflowStatus)
    }

    private fun verifyConfirmationCommand(execution: Commands.Confirmation,tx: LedgerTransaction,signers: Set<PublicKey>) = requireThat {
        val logger = loggerFor<StateAndContract>()

        val inputState = tx.inputStates.find { it is ExecutionState } as ExecutionState

        val confirmationState = tx.outputStates.find { it is ConfirmationState } as ConfirmationState
        "Both parties together only may sign issue transaction." using (signers == keysFromParticipants(confirmationState))
        val executionState = tx.outputStates.find { it is ExecutionState } as ExecutionState
        "Both parties together only may sign issue transaction." using (signers == keysFromParticipants(executionState))

        logger.debug("------ Input state execution: "+ inputState.execution())
        logger.debug("------ Output state affirm: "+ confirmationState.confirmation())
        logger.debug("------ Output state execution: "+ executionState.execution())

        logger.debug("------ Input state execution status: "+ inputState.workflowStatus)
        logger.debug("------ Output state affirm status: "+ confirmationState.confirmation().status)
        logger.debug("------ Output state execution status: "+ executionState.workflowStatus)

        "Status of out execution state and affirmation state are same." using (ConfirmationStatusEnum.CONFIRMED.name == executionState.workflowStatus)
    }

    private fun keysFromParticipants(execution:ContractState): Set<PublicKey>{
        return execution.participants.map {
            it.owningKey
        }.toSet()
    }







}











