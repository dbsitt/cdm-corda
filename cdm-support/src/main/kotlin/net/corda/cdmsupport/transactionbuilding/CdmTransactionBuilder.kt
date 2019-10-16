package net.corda.cdmsupport.transactionbuilding

import net.corda.cdmsupport.CDMEvent
import net.corda.cdmsupport.ExecutionAlreadyExists
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.extensions.*
import net.corda.cdmsupport.functions.SETTLEMENT_AGENT_STR
import net.corda.cdmsupport.functions.extractParty
import net.corda.cdmsupport.functions.hashCDM
import net.corda.cdmsupport.states.ExecutionState
import net.corda.cdmsupport.vaultquerying.CdmVaultQuery
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.loggerFor
import org.isda.cdm.*
import org.isda.cdm.metafields.MetaFields
import java.lang.IllegalStateException


class CdmTransactionBuilder(notary: Party? = null,
                            val event: Event,
                            val cdmVaultQuery: CdmVaultQuery) : TransactionBuilder(notary) {

    val participantsFromInputs = mutableSetOf<AbstractParty>()

    init {

        event.primitive.allocation?.forEach { processAllocationPrimitive(it) }
        //event.primitive.allocation?.forEach { processAllocationPrimitiveNew(it) }
        event.primitive.execution?.forEach { processeExecutionPrimitive(it) }
    }

    @Throws(RuntimeException::class)
    private fun processAllocationPrimitive(allocationPrimitive: AllocationPrimitive) {

        val logger = loggerFor<CdmTransactionBuilder>()

        val executionLineage = event.lineage.executionReference[0].globalReference

        if (allocationPrimitive.validateLineageAndTotals(serviceHub!!, executionLineage)) {
            val settlementAgent = serviceHub.identityService.partiesFromName(SETTLEMENT_AGENT_STR,true).single()
            val inputState = cdmVaultQuery.getCdmExecutionStateByMetaGlobalKey(executionLineage)

            val closedState = inputState.state.data.execution().closedState?.state?.name.orEmpty()

            logger.debug("INPUT_STATE.closedState: $closedState");

            if("ALLOCATED" == closedState){
                throw Exception("Block Trade is already allocated")
            }

            addInputState(inputState)

            val outputBeforeState = createExecutionState(allocationPrimitive.after.originalTrade.execution)
            val participantsWithSAWithoutClient = mutableListOf(settlementAgent)
            participantsWithSAWithoutClient.addAll(outputBeforeState.participants.filter { !it.name.organisation.contains("Client") })
            check(participantsWithSAWithoutClient.size == 3) {"trade between 2 broker, settlement_agent only"}
            val clientRef = outputBeforeState.execution().party.first { it.value.name.value.contains("Client")}
            val beforeExecution = outputBeforeState.execution()
            val beforeExecutionBuilder = beforeExecution.toBuilder()
            val settlementTermsBuilder = beforeExecution.settlementTerms.toBuilder().setMeta(MetaFields.builder().setGlobalKey(hashCDM(beforeExecution.settlementTerms)).build())

            beforeExecutionBuilder.party.removeIf{it.globalReference == clientRef.globalReference}
            beforeExecutionBuilder.partyRole.removeIf{it.partyReference.globalReference == clientRef.globalReference}
            beforeExecutionBuilder.setSettlementTerms(settlementTermsBuilder.build())
            val outputBeforeStateWithSettlementAgent = outputBeforeState.copy(participants = participantsWithSAWithoutClient,
                    executionJson = serializeCdmObjectIntoJson(beforeExecutionBuilder.build()), workflowStatus = ClosedStateEnum.ALLOCATED.name)

            check(outputBeforeStateWithSettlementAgent.execution().party.size == 2) {"json also need to contains 2 parties only: 2 brokers"}
            check(outputBeforeStateWithSettlementAgent.execution().partyRole.size == 4) {"json also need to contains 4 parties only: executing_entity, counterparty, seller, buyer"}
            val outputAfterStates = allocationPrimitive.after.allocatedTrade.map { createExecutionStateFromAfterAllocation(it.execution, executionLineage) }

            val outputIndexOnBefore = this.addOutputStateReturnIndex(outputBeforeStateWithSettlementAgent, CDMEvent.ID)

            addCommand(CDMEvent.Commands.Execution(outputIndexOnBefore), outputBeforeStateWithSettlementAgent.participants.map { it.owningKey }.toList())

            outputAfterStates.forEach {
                val outputIndexOnAfter = this.addOutputStateReturnIndex(it, CDMEvent.ID)
                addCommand(CDMEvent.Commands.Execution(outputIndexOnAfter), it.participants.map { p -> p.owningKey }.toSet().toList())
            }
        }
    }

    @Throws(RuntimeException::class)
    private fun processAllocationPrimitiveNew(allocationPrimitive: AllocationPrimitive) {

        val logger = loggerFor<CdmTransactionBuilder>()

        val executionLineage = event.lineage.executionReference[0].globalReference

        if (allocationPrimitive.validateLineageAndTotals(serviceHub!!, executionLineage)) {
            val inputState = cdmVaultQuery.getCdmExecutionStateByMetaGlobalKey(executionLineage)

            val closedState = inputState.state?.data?.execution()?.closedState?.state?.name?.orEmpty()

            logger.debug("INPUT_STATE.closedState: $closedState");

            if("ALLOCATED" == closedState){

                throw Exception("Block Trade is already allocated")
            }

            addInputState(inputState)

            val outputBeforeState = createExecutionState(allocationPrimitive.after.originalTrade.execution)

            val outputAfterStates = allocationPrimitive.after.allocatedTrade.map { createExecutionStateFromAfterAllocation(it.execution, executionLineage) }

            val outputIndexOnBefore = this.addOutputStateReturnIndex(outputBeforeState, CDMEvent.ID)
            addCommand(CDMEvent.Commands.Execution(outputIndexOnBefore), outputBeforeState.participants.map { it.owningKey }.toSet().toList())

            outputAfterStates.forEach {
                val outputIndexOnAfter = this.addOutputStateReturnIndex(it, CDMEvent.ID)
                addCommand(CDMEvent.Commands.Allocation(outputIndexOnAfter), it.participants.map { p -> p.owningKey }.toSet().toList())
            }
        }
    }

    private fun processeExecutionPrimitive(executionPrimitive: ExecutionPrimitive) {
        if  (cdmVaultQuery.getExecutions().any { it.meta.globalKey == executionPrimitive.after.execution.meta.globalKey }) {
            throw ExecutionAlreadyExists(executionPrimitive.after.execution.meta.globalKey)
        }

        val outputState = createExecutionState(executionPrimitive.after.execution, status = "EXECUTED")
        val outputIndex = addOutputStateReturnIndex(outputState, CDMEvent.ID)
        addCommand(CDMEvent.Commands.Execution(outputIndex), this.outputStates().flatMap { it.data.participants }.map { it.owningKey }.toSet().toList())
    }

    fun getPartiesToSign(): List<Party> {
        return this.commands.flatMap { it.signers }.toSet().map { serviceHub!!.networkMapCache.getNodesByLegalIdentityKey(it).first().legalIdentities.first() }
    }

    override fun addInputState(stateAndRef: StateAndRef<*>): TransactionBuilder {
        participantsFromInputs.addAll(stateAndRef.state.data.participants)
        return super.addInputState(stateAndRef)
    }

    fun addOutputStateReturnIndex(state: ContractState, contract: ContractClassName): Int {
        addOutputState(state, contract)
        return indexOfCurrentOutputState()
    }

    private fun createExecutionState(execution: Execution, status: String = AffirmationStatusEnum.UNAFFIRMED.name): ExecutionState {
        val executionWithParties : Execution = execution.createExecutionWithPartiesFromEvent(event)
        val json = serializeCdmObjectIntoJson(executionWithParties)
        val participants = executionWithParties.mapPartyToCordaX500ForExecution(serviceHub!!)

        return ExecutionState(json, event.meta.globalKey, status, participants, UniqueIdentifier())
    }

    private fun createExecutionStateFromAfterAllocation(execution: Execution, executionLineage: String) : ExecutionState {
        val executionWithParties : Execution = execution.createExecutionWithPartiesFromAllocationEvent(event, executionLineage)
        val counterParty = extractParty(executionWithParties, PartyRoleEnum.COUNTERPARTY)
        executionWithParties.party.removeIf { it.globalReference == counterParty.globalReference }
        executionWithParties.partyRole.removeIf {it.partyReference.globalReference == counterParty.globalReference}
        val json = serializeCdmObjectIntoJson(executionWithParties)
        val participants = executionWithParties.mapPartyToCordaX500ForAllocation(serviceHub!!)
        check(executionWithParties.party.size == 2) {"should be 2 party here only, executing_entity and client"}
        check(executionWithParties.partyRole.size == 4) {"should contain buyer, seller, executing_entity and client"}
        check(participants.size == 2) { "participant for alloc should be client and broker only" }
        return ExecutionState(json, event.meta.globalKey, AffirmationStatusEnum.UNAFFIRMED.name, participants, UniqueIdentifier())
    }

    private fun indexOfCurrentOutputState(): Int {
        return outputStates().size - 1
    }

    private fun getAllParticipantsAcrossAllInputsAndOutputs(): List<AbstractParty> {
        return (participantsFromInputs + outputStates().flatMap { it.data.participants }.toSet()).toList()
    }

}
