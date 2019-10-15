package net.corda.cdmsupport.functions

import com.rosetta.model.lib.RosettaModelObject
import net.corda.cdmsupport.states.ExecutionState
import org.isda.cdm.*
import org.isda.cdm.metafields.FieldWithMetaString
import org.isda.cdm.metafields.MetaFields
import org.isda.cdm.metafields.ReferenceWithMetaParty
import org.isda.cdm.rosettakey.SerialisingHashFunction
import java.lang.StringBuilder
import java.util.Random

const val SETTLEMENT_AGENT_STR = "SettlementAgent"
const val COLLATERAL_AGENT_STR = "CollateralAgent"
const val CHAR_STR = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

fun generateIdentifier(issuer: ReferenceWithMetaParty, value: String): Identifier {
    val builder = Identifier.builder()
            .addAssignedIdentifier(AssignedIdentifier.builder().setIdentifier(FieldWithMetaString.builder().setValue(value).build()).setVersion(1).build())
            .setIssuerReference(issuer)
    return builder.setMeta(MetaFields.builder().setGlobalKey(hashCDM(builder.build())).build()).build()
}

fun generateRandomID36(): Int {
    return Random().nextInt(36)
}

fun generateRandomID26(): Int {
    return Random().nextInt(26)
}

fun generateNameKey(): String {
    val builder = StringBuilder()
    builder.append(CHAR_STR.get(generateRandomID26()))
    for (i in 0..11) {
        builder.append(CHAR_STR.get(generateRandomID36()))
    }
    println("generateNameKey ##############################")
    println(builder.toString())
    println("generateNameKey ##############################")
    return builder.toString()
}

fun hashCDM(obj: RosettaModelObject): String {
    return SerialisingHashFunction().hash(obj)
}

fun extractParty(state: ExecutionState, roleEnum: PartyRoleEnum): ReferenceWithMetaParty {
    val clientRole = state.execution().partyRole.first { it.role == roleEnum }

    return state.execution().party.first { it.value.meta.globalKey == clientRole.partyReference.globalReference }
}

fun extractPartyRole(state: ExecutionState, roleEnum: PartyRoleEnum): PartyRole {
    return state.execution().partyRole.first { it.role == roleEnum }
}

fun generateParty(accountName: String): Party {
    val partyIdRandomStr = generateNameKey()
    val partyId = "${accountName}_ID#0_$partyIdRandomStr"
    val accountBuilder = Account.builder()
            .setAccountName(FieldWithMetaString.builder().setValue(accountName).build())
            .setAccountNumber(FieldWithMetaString.builder().setValue("$accountName#AccNumber").build())

    val account = accountBuilder.setMeta(MetaFields.builder().setGlobalKey(hashCDM(accountBuilder.build())).build()).build()
    var party = Party.builder()
            .setAccount(account)
            .setName(FieldWithMetaString.builder().setValue(partyId.split("_")[0]).build())
            .addPartyId(FieldWithMetaString.builder().setValue(partyId).build())
            .build()
    val hash = SerialisingHashFunction().hash(party)
    party = party.toBuilder().setMeta(MetaFields.builder().setGlobalKey(hash).setExternalKey(partyId).build()).build()
    return party
}