package net.corda.cdmsupport.functions

import com.rosetta.model.lib.RosettaModelObject
import com.rosetta.model.lib.records.Date
import com.rosetta.model.lib.records.DateImpl
import net.corda.cdmsupport.ExecutionRequest
import net.corda.cdmsupport.states.ExecutionState
import org.isda.cdm.*
import org.isda.cdm.metafields.*
import org.isda.cdm.rosettakey.SerialisingHashFunction
import java.lang.StringBuilder
import java.math.BigDecimal
import java.util.Random

const val SETTLEMENT_AGENT_STR = "SettlementAgent"
const val COLLATERAL_AGENT_STR = "CollateralAgent"
const val CLIENT1_STR = "Client1"
const val CLIENT2_STR = "Client2"
const val CLIENT3_STR = "Client3"
const val BROKER1_STR = "Broker1"
const val BROKER2_STR = "Broker2"
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

fun checkIfBuyTransferBetweenBrokerAndClient(execution: Execution): Boolean {
    return execution.partyRole.filter { it.role == PartyRoleEnum.BUYER }.size == 2
}

fun checkIfSellTransferBetweenBrokerAndClient(execution: Execution): Boolean {
    return execution.partyRole.filter { it.role == PartyRoleEnum.SELLER }.size == 2
}

fun extractParty(state: Execution, roleEnum: PartyRoleEnum): ReferenceWithMetaParty {
    val clientRole = state.partyRole.first { it.role == roleEnum }

    return state.party.first { it.value.meta.globalKey == clientRole.partyReference.globalReference }
}

fun extractPartyRole(state: ExecutionState, roleEnum: PartyRoleEnum): PartyRole {
    return state.execution().partyRole.first { it.role == roleEnum }
}

fun generateParty(partyName: String, accountName: String = "", hash: String = ""): Party {
    val partyIdRandomStr = if (hash.isBlank()) hash else generateNameKey()
    val partyId = "${partyName}_ID#0_$partyIdRandomStr"
    val accountBuilder = Account.builder()
            .setAccountName(FieldWithMetaString.builder().setValue(partyName).build())
            .setAccountNumber(FieldWithMetaString.builder().setValue("$partyName#AccNumber").build())

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

fun generateMoney(amount: BigDecimal): Money{
    return Money.builder()
            .setAmount(amount)
            .setCurrency(FieldWithMetaString.builder().setValue("USD").build())
            .build()
}

fun generatePartyRole(partyRef: String, role: PartyRoleEnum): PartyRole {
    return PartyRole.builder()
            .setPartyReference(ReferenceWithMetaParty.builder()
                    .setGlobalReference(partyRef)
                    .build())
            .setRole(role)
            .build()
}

fun toCDMDate(str: String): Date {
    val items = str.split("/")
    return DateImpl(items[2].toInt(),items[1].toInt(), items[0].toInt())
}

fun createEvent(request: ExecutionRequest) : Event {
    val partyMap = listOf(AgentHolder.client1ACT0, AgentHolder.client1ACT1, AgentHolder.client1ACT2,
            AgentHolder.client2ACT0, AgentHolder.client2ACT1, AgentHolder.client2ACT2,
            AgentHolder.client3ACT0, AgentHolder.client3ACT1, AgentHolder.client3ACT2,
            AgentHolder.broker1, AgentHolder.broker2).map { it.account.accountName.value to it }.toMap()

    val client = partyMap[request.client]!!
    val executingEntity = partyMap[request.executingEntity]!!
    val counterParty = partyMap[request.counterParty]!!
    val roleList = mutableListOf(
            generatePartyRole(client.meta.globalKey, PartyRoleEnum.CLIENT),
            generatePartyRole(executingEntity.meta.globalKey, PartyRoleEnum.EXECUTING_ENTITY),
            generatePartyRole(counterParty.meta.globalKey, PartyRoleEnum.COUNTERPARTY)
    )
    if (request.buySell == "buy") {
        roleList.add(generatePartyRole(client.meta.globalKey, PartyRoleEnum.BUYER))
        roleList.add(generatePartyRole(executingEntity.meta.globalKey, PartyRoleEnum.BUYER))
        roleList.add(generatePartyRole(counterParty.meta.globalKey, PartyRoleEnum.SELLER))
    } else {
        roleList.add(generatePartyRole(client.meta.globalKey, PartyRoleEnum.SELLER))
        roleList.add(generatePartyRole(executingEntity.meta.globalKey, PartyRoleEnum.SELLER))
        roleList.add(generatePartyRole(counterParty.meta.globalKey, PartyRoleEnum.BUYER))
    }

    val executionBuilder = Execution.builder()
            .setPrice(Price.builder()
                    .setNetPrice(ActualPrice.builder()
                            .setAmount(BigDecimal.valueOf(request.price))
                            .setCurrency(FieldWithMetaString.builder().setValue("USD").build())
                            .build())

                    .build())
            .setProduct(Product.builder()
                    .setSecurity(Security.builder()
                            .setBond(Bond.builder()
                                    .setProductIdentifier(ProductIdentifier.builder()
                                            .setSource(ProductIdSourceEnum.CUSIP)
                                            .addIdentifier(FieldWithMetaString.builder().setValue(request.product).build())
                                            .build())
                                    .build())
                            .build())
                    .build())
            .setQuantity(Quantity.builder().setAmount(BigDecimal.valueOf(request.quantity)).build())
            .addIdentifier(generateIdentifier(ReferenceWithMetaParty.builder().setGlobalReference(executingEntity.meta.globalKey).build(), "W3S0XZGEM4S82"))
            .setSettlementTerms(SettlementTerms.builder()
                    .setSettlementAmount(Money.builder()
                            .setCurrency(FieldWithMetaString.builder().setValue("USD").build())
                            .setAmount(BigDecimal.valueOf(request.price).multiply(BigDecimal.valueOf(request.quantity)).divide(BigDecimal.valueOf(100)))
                            .build())
                    .setSettlementDate(AdjustableOrRelativeDate.builder()
                            .setAdjustableDate(AdjustableDate.builder()
                                    //TODO change to T+1 day
                                    .setUnadjustedDate(toCDMDate(request.eventDate))
                                    .build())
                            .build())
                    .build())
            .setTradeDate(FieldWithMetaDate.builder().setValue(toCDMDate(request.tradeDate)).build())
    for (role in roleList) {
        executionBuilder.addPartyRole(role)
    }

    val execution = executionBuilder.setMeta(MetaFields.builder().setGlobalKey(hashCDM(executionBuilder.build())).build()).build()

    val eventBuilder =  Event.builder()
            .setEventDate(toCDMDate(request.tradeDate))
            .addParty(client)
            .addParty(executingEntity)
            .addParty(counterParty)
            .setPrimitive(PrimitiveEvent.builder()
                    .addExecution(ExecutionPrimitive.builder()
                            .setAfter(org.isda.cdm.ExecutionState.builder()
                                    .setExecution(execution)
                                    .build())
                            .build())

                    .build())
            .setEventEffect(EventEffect.builder()
                    .addEffectedExecution(ReferenceWithMetaExecution.builder()
                            .setGlobalReference(execution.meta.globalKey)
                            .build())
                    .build())
            .addEventIdentifier(Identifier.builder()
                    .setIssuerReference(ReferenceWithMetaParty.builder().setGlobalReference(executingEntity.meta.globalKey).build())
                    .build())

    return eventBuilder.setMeta(MetaFields.builder().setGlobalKey(hashCDM(eventBuilder.build())).build()).build()
}