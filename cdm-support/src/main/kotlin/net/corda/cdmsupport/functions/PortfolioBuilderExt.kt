package net.corda.cdmsupport.functions

import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper
import net.corda.cdmsupport.extensions.PortfolioInstructions
import org.isda.cdm.*
import org.isda.cdm.metafields.*
import org.joda.time.LocalDate
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.json.JsonObject


fun generateSettledPortfolioRpt(instructions:JsonObject, settlementEvent:Event): Portfolio{

    val portfolioInstr = PortfolioInstructions.Builder(instructions).build()

    val account = portfolioInstr.client?.account
    val security = portfolioInstr.security
    val prod = Product.builder().setSecurity(security).build()
    val acctNumber = account?.accountNumber?.value

    // fetch SETTLED TransferStatus
    val tp = settlementEvent.primitive.transfer?.find{
        it.status ==TransferStatusEnum.SETTLED
    } as TransferPrimitive

    val aggBuilder = AggregationParameters.builder()

    val party = settlementEvent.party?.first { acctNumber == it.account.accountNumber.value } as Party
    val r = ReferenceWithMetaParty.ReferenceWithMetaPartyBuilder().setGlobalReference(party.meta.globalKey).build()




    val dateTime = portfolioInstr.portfolioDate

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





