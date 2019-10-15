package net.corda.cdmsupport.functions

import net.corda.cdmsupport.states.ExecutionState
import org.isda.cdm.*
import org.isda.cdm.metafields.*
import java.math.BigDecimal

fun allocationBuilderFromExecution(amount1: BigDecimal, amount2: BigDecimal, state: ExecutionState): Event {
//        val subAccount1 = generateParty("Client1_SubAccount1", "Client1_ID1234")
//        val subAccount2 = generateParty("Client1_SubAccount2", "Client1_ID1235")

//        Refer to client, you can create subAccount party using above code, but basiclly in this example corda map party by name

    val client = extractParty(state, PartyRoleEnum.CLIENT)
    val broker = extractParty(state, PartyRoleEnum.EXECUTING_ENTITY)
    val counterParty = extractParty(state, PartyRoleEnum.COUNTERPARTY)
    val trade1 = generateTrade(amount1, state.execution(), client.value)
    val trade2 = generateTrade(amount2, state.execution(), client.value)
    var allocation = Event.builder()
            .setAction(ActionEnum.NEW)
            .setEventEffect(EventEffect.builder()
                    .addEffectedExecution(ReferenceWithMetaExecution.builder().setGlobalReference(trade1.execution.meta.globalKey).build())
                    .addEffectedExecution(ReferenceWithMetaExecution.builder().setGlobalReference(trade2.execution.meta.globalKey).build())
                    .build())
            .setLineage(Lineage.builder()
                    .addEventReference(ReferenceWithMetaEvent.ReferenceWithMetaEventBuilder()
                            .setGlobalReference(state.eventReference).build())
                    .addExecutionReference(ReferenceWithMetaExecution.builder()
                            .setGlobalReference(state.execution().meta.globalKey)
                            .build())
                    .build())
            .addParty(client.value)
            .addParty(counterParty.value)
            .addParty(broker.value)
            .setPrimitive(PrimitiveEvent.builder()
                    .addAllocation(
                            AllocationPrimitive.builder()
                                    .setAfter(AllocationOutcome.builder()
                                            .addAllocatedTrade(trade1)
                                            .addAllocatedTrade(trade2)
                                            .setOriginalTrade(Trade.builder().setExecution(state.execution()).build())
                                            .build())
                                    .setBefore(Trade.builder().setExecution(state.execution()).build())
                                    .build()
                    )
                    .build())
            .build()
    allocation = allocation.toBuilder().setMeta((MetaFields.builder().setGlobalKey(hashCDM(allocation))).build()).build()
    return allocation
}



private fun generateTrade(amount: BigDecimal, execution: Execution, party: Party): Trade {
    val settlementAmount = amount.multiply(execution.price.netPrice.amount)
    val settlementTerms = execution.settlementTerms.toBuilder().setSettlementAmount(Money.builder().setAmount(settlementAmount).build()).build()
    val executionBuilder = Execution.builder()
            .setExecutionType(ExecutionTypeEnum.ELECTRONIC)
            .setExecutionVenue(LegalEntity.builder().setName(FieldWithMetaString.builder().setValue("ExecutionVenue").build()).build())
            .addIdentifier(generateIdentifier(execution.identifier[0].issuerReference, "allocate to  " + party.name.value + "-Identifier"))
            .setPrice(execution.price)
            .setProduct(execution.product)
            .setQuantity(Quantity.builder().setAmount(amount).build())
            .setTradeDate(execution.tradeDate)
            .setSettlementTerms(settlementTerms)

    //        keep counter party and executing entity
    val client = execution.partyRole.stream().filter { role -> role.role == PartyRoleEnum.CLIENT }.findFirst().orElseThrow { IllegalStateException("Should have client role here") }
    execution.partyRole.stream().filter { partyRole -> partyRole.partyReference.globalReference != client.partyReference.globalReference }
            .forEach { executionBuilder.addPartyRole(it) }

    executionBuilder.addPartyRole(PartyRole.builder().setRole(PartyRoleEnum.BUYER).setPartyReference(ReferenceWithMetaParty.builder().setGlobalReference(party.meta.globalKey).build()).build())
    executionBuilder.addPartyRole(PartyRole.builder().setRole(PartyRoleEnum.CLIENT).setPartyReference(ReferenceWithMetaParty.builder().setGlobalReference(party.meta.globalKey).build()).build())
    executionBuilder.setMeta(MetaFields.builder().setGlobalKey(hashCDM(executionBuilder.build())).build())

    return Trade.builder()
            .setExecution(executionBuilder.build())
            .build()
}
