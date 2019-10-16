package net.corda.cdmsupport.functions

import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper
import net.corda.cdmsupport.extensions.PortfolioInstructions
import org.isda.cdm.*
import org.isda.cdm.metafields.*
import org.joda.time.LocalDate
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.json.JsonObject


fun generateTradedPortfolioRpt(instructions:JsonObject, allocationEvent:Event): Portfolio{


    //retrieve info from instructions
    /*
    val accJsonString = instructions.getJsonObject("PortfolioInstructions").getJsonObject("Client").getJsonObject("account").toString()
    val secJsonString = instructions.getJsonObject("PortfolioInstructions").getJsonObject("security").toString()
    val dateJsonString = instructions.getJsonObject("PortfolioInstructions").getString("PortfolioDate")


    val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
    val account = rosettaObjectMapper.readValue<Account>(accJsonString,Account::class.java)
    val security = rosettaObjectMapper.readValue<Security>(secJsonString,Security::class.java)

    */


    val portfolioInstr = PortfolioInstructions.Builder(instructions).build()

    val account = portfolioInstr.client?.account
    val security = portfolioInstr.security


    val prod = Product.builder().setSecurity(security).build()
    val acctNumber = account?.accountNumber?.value


    val party = allocationEvent.party.find{ it.account?.accountNumber?.value == acctNumber} as Party



    val aggBuilder = AggregationParameters.builder()

    /*
    val df = SimpleDateFormat("yyyy-MM-dd")
    val date = df.parse(dateJsonString)

    val r = ReferenceWithMetaParty.ReferenceWithMetaPartyBuilder().setGlobalReference(party.meta.globalKey).build()
    val localDate = LocalDate.fromDateFields(date)


    val y = localDate.year
    val m = localDate.monthOfYear
    val d = localDate.dayOfMonth

    val dateTime = ZonedDateTime.of(y, m, d,
            0, 0, 0, 0, ZoneId.systemDefault())


    */

    val dateTime = portfolioInstr.portfolioDate
    val r = ReferenceWithMetaParty.ReferenceWithMetaPartyBuilder().setGlobalReference(party.meta.globalKey).build()

    aggBuilder.addParty(r)
            .setDateTime(dateTime)
            .addProduct(prod)

    val psBuilder = PortfolioState.builder()

    psBuilder.setMeta(MetaFields.builder().setGlobalKey("YlKpJEBIVtSTVbIh9/NWs5nsE5VdnXSml/+T8ZdgzQE=").build())


    val lineageBuilder = Lineage.LineageBuilder()

    lineageBuilder.addEventReference(ReferenceWithMetaEvent.ReferenceWithMetaEventBuilder()
            .setGlobalReference(allocationEvent.meta.globalKey).build())



    val posBuilder = Position.builder()
    allocationEvent.primitive.allocation.forEach {

        val execution = it.after.allocatedTrade.first().execution
        lineageBuilder.addTransferReference(
                ReferenceWithMetaTransferPrimitive.builder()
                        .setGlobalReference(execution.meta.globalKey).build())



        val prod = execution.product
        val quantity = execution.quantity

        val amt = execution.settlementTerms.settlementAmount.amount.multiply(BigDecimal(-1))
        val cur = execution.settlementTerms.settlementAmount.currency

        val moneyBuilder = Money.builder()

        moneyBuilder.setAmount(amt).setCurrency(cur)
        posBuilder.setCashBalance(moneyBuilder.build())

        posBuilder.setPositionStatus(PositionStatusEnum.EXECUTED)
                .setProduct(prod).setQuantity(quantity)
        psBuilder.addPositionsBuilder(posBuilder)
    }


    psBuilder.setLineage(
            lineageBuilder.build())




    return Portfolio.builder().setAggregationParametersBuilder(aggBuilder).setPortfolioStateBuilder(psBuilder).build()
}