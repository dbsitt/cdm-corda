package net.corda.cdmsupport.events

import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper
import groovy.json.JsonBuilder
import net.corda.cdmsupport.eventparsing.readEventFromJson
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import org.isda.cdm.*
import org.isda.cdm.metafields.*
import org.junit.Test
import java.io.FileReader
import java.io.StringReader
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.json.Json
import kotlin.test.assertNotNull


class PortfolioReportSettledTest {


    /*

    {
      "PortfolioInstructions": {
        "PortfolioDate": "2019-10-17",
        "Client": {
          "account": {
            "accountName": {
              "value": "Client1_ACT#0"
            },
            "accountNumber": {
              "value": "Client1_ACT#0_NQQVRTLFIT4HZ"
            }
          },
          "name": {
            "value": "Client1"
          },
          "partyId": [
            {
              "value": "Client1_ID#0_NH90YY6QYVYHM"
            }
          ]
        },
        "security": {
          "bond": {
            "productIdentifier": {
              "identifier": [
                {
                  "value": "DH0371475458"
                }
              ],
              "source": "CUSIP"
            }
          }
        }
      }
    }

    */


    @Test
    fun runTest(){



        //val instructions = Json.createReader(FileReader("/${samplesDirectory}/instructions.json")).readObject()

        try {

            val initString  = readTextFromFile("/settlement/UC6_Portfolio_Instructions_20191017.json")

            val instructions = Json.createReader(StringReader(initString)).readObject()

            println(instructions.getJsonObject("PortfolioInstructions").getJsonObject("Client").getJsonObject("account"))
            println(instructions.getJsonObject("PortfolioInstructions").getJsonObject("security"))

            val accJsonString = instructions.getJsonObject("PortfolioInstructions").getJsonObject("Client").getJsonObject("account").toString()
            val secJsonString = instructions.getJsonObject("PortfolioInstructions").getJsonObject("security").toString()
            val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()

            val account = rosettaObjectMapper.readValue<Account>(accJsonString,Account::class.java)
            val security = rosettaObjectMapper.readValue<Security>(secJsonString,Security::class.java)
            //rosettaObjectMapper.readValue<Security>(jsonString, Security::class.java)

            val acctNumber = account.accountNumber.value
            //val acctName = "Client1_ACT#0"
            //val partyId = "Client1_ID#0_NH90YY6QYVYHM"
            //val partyName = "Client1"



            //readTextFromFile("/${samplesDirectory}/UC3_affirmation_T1.1_GJ.json")
            val settlementEvent = readEventFromJson("/settlement/UC5_Settlement_AT1.2.json")

            //println(settlementEvent)
            //println("--------- 1")

            val aggBuilder = AggregationParameters.builder()


            val party = settlementEvent.party?.first { acctNumber == it.account.accountNumber.value } as Party


            //println(party.meta.globalKey)
            val r = ReferenceWithMetaParty.ReferenceWithMetaPartyBuilder().setGlobalReference(party.meta.globalKey).build()
            //val r2 =ReferenceWithMetaParty.ReferenceWithMetaPartyBuilder().setValue(Party.PartyBuilder().setMeta(party.meta).build()).build()
            aggBuilder.addParty(r)



            settlementEvent.primitive.transfer?.forEach {
                val y = it.settlementDate.unadjustedDate.year
                val m = it.settlementDate.unadjustedDate.month
                val d = it.settlementDate.unadjustedDate.day
                val dateTime = ZonedDateTime.of(y, m, d,
                        0, 0, 0, 0, ZoneId.of("UTC"))
                aggBuilder.dateTime = dateTime
            }



            val prod = Product.builder().setSecurity(security).build()
            aggBuilder.addProduct(prod)

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





            val portfolio = Portfolio.builder().setAggregationParametersBuilder(aggBuilder).setPortfolioStateBuilder(psBuilder).build()

            val json = serializeCdmObjectIntoJson(portfolio)


            //println(json)
            assertNotNull(json)


        }catch(ex:Exception){
            ex.printStackTrace(System.out)
        }

    }
}