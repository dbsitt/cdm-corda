package net.corda.cdmsupport.functions

import net.corda.cdmsupport.functions.AgentHolder.Factory.settlementAgentParty
import net.corda.cdmsupport.states.ExecutionState
import net.corda.core.contracts.UniqueIdentifier
import org.isda.cdm.*
import org.isda.cdm.metafields.*

fun confirmationBuilderFromExecution(state : ExecutionState) : Confirmation {
    val confirmationBuilder = Confirmation.builder()
            .setLineage(Lineage.LineageBuilder()
                    .addEventReference(ReferenceWithMetaEvent.ReferenceWithMetaEventBuilder()
                            .setGlobalReference(state.eventReference).build())
                    .addExecutionReference(ReferenceWithMetaExecution.ReferenceWithMetaExecutionBuilder()
                            .setGlobalReference(state.execution().meta.globalKey).build())

                    .build())
            .setStatus(ConfirmationStatusEnum.CONFIRMED)

    val client = extractParty(state, PartyRoleEnum.CLIENT)
    val broker1 = extractParty(state, PartyRoleEnum.EXECUTING_ENTITY)
    val clientRole = extractPartyRole(state, PartyRoleEnum.CLIENT)
    val broker1Role = extractPartyRole(state, PartyRoleEnum.EXECUTING_ENTITY)
    confirmationBuilder
            .addIdentifier(Identifier.IdentifierBuilder()
                    .addAssignedIdentifier(AssignedIdentifier.AssignedIdentifierBuilder()
                            .setIdentifier(FieldWithMetaString.FieldWithMetaStringBuilder().setValue(UniqueIdentifier().id.toString().replace("-", ""))
                                    .build())
                            .setVersion(1)
                            .build())
//                    Check who issue here: client or broker
                    .setIssuerReference(client)
                    .setMeta(MetaFields.MetaFieldsBuilder().setGlobalKey(UniqueIdentifier().id.toString().replace("-", "")).build())
                    .build())
            .addParty(client.value)
            .addParty(broker1.value)
            .addParty(settlementAgentParty)
            .addPartyRole(clientRole)
            .addPartyRole(broker1Role)
            .addPartyRole(PartyRole.builder().setPartyReference(ReferenceWithMetaParty.builder()
                    .setGlobalReference(settlementAgentParty.meta.globalKey).build())
                    .setRole(PartyRoleEnum.SETTLEMENT_AGENT)
                    .build())

    return confirmationBuilder.build()
}