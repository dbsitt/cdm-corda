package net.corda.cdmsupport.functions

import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.states.ExecutionState
import org.isda.cdm.*
import org.isda.cdm.metafields.*
import org.json.simple.JSONObject
import java.math.BigDecimal
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.json.JsonObject


fun generateSettledPortfolioRpt(instructions:JsonObject, settlementEvent:Event): Portfolio{


    //retrieve info from instructions
    val accJsonString = instructions.getJsonObject("PortfolioInstructions").getJsonObject("Client").getJsonObject("account").toString()
    val secJsonString = instructions.getJsonObject("PortfolioInstructions").getJsonObject("security").toString()

    val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
    val account = rosettaObjectMapper.readValue<Account>(accJsonString,Account::class.java)
    val security = rosettaObjectMapper.readValue<Security>(secJsonString,Security::class.java)
    val prod = Product.builder().setSecurity(security).build()
    val acctNumber = account.accountNumber.value

    // fetch SETTLED TransferStatus
    val tp = settlementEvent.primitive.transfer?.find{
        it.status ==TransferStatusEnum.SETTLED
    } as TransferPrimitive

    val aggBuilder = AggregationParameters.builder()

    val party = settlementEvent.party?.first { acctNumber == it.account.accountNumber.value } as Party
    val r = ReferenceWithMetaParty.ReferenceWithMetaPartyBuilder().setGlobalReference(party.meta.globalKey).build()

    val y = tp.settlementDate.unadjustedDate.year
    val m = tp.settlementDate.unadjustedDate.month
    val d = tp.settlementDate.unadjustedDate.day

    val dateTime = ZonedDateTime.of(y, m, d,
            0, 0, 0, 0, ZoneId.systemDefault())




    aggBuilder.addParty(r)
            .setDateTime(dateTime)
            .addProduct(prod)

    val psBuilder = PortfolioState.builder()

    psBuilder.setMeta(MetaFields.builder().setGlobalKey("YlKpJEBIVtSTVbIh9/NWs5nsE5VdnXSml/+T8ZdgzQE=").build())

    psBuilder.setLineage(
            Lineage.LineageBuilder()
                    .addEventReference(
                            ReferenceWithMetaEvent.ReferenceWithMetaEventBuilder()
                                    .setGlobalReference(settlementEvent.meta.globalKey).build()
                    ).addTransferReference(
                            ReferenceWithMetaTransferPrimitive.builder()
                                    .setGlobalReference(settlementEvent.eventEffect.transfer.first().globalReference).build()).build())


    val posBuilder = Position.builder()
    val secTrans = tp.securityTransfer.first()
    val quantity = secTrans.quantity

    val cashTrans = tp.cashTransfer.first()

    val amt = cashTrans.amount.amount.multiply(BigDecimal(-1))
    val cur = cashTrans.amount.currency

    val moneyBuilder = Money.builder()

    moneyBuilder.setAmount(amt).setCurrency(cur)
    posBuilder.setCashBalance(moneyBuilder.build())

    posBuilder.setPositionStatus(PositionStatusEnum.SETTLED)
            .setProduct(prod).setQuantity(Quantity.QuantityBuilder().setAmount(quantity).build())
    psBuilder.addPositionsBuilder(posBuilder)


    return Portfolio.builder().setAggregationParametersBuilder(aggBuilder).setPortfolioStateBuilder(psBuilder).build()
}


