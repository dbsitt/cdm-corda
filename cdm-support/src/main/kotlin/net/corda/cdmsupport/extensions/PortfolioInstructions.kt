package net.corda.cdmsupport.extensions
import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper
import org.isda.cdm.*
import org.isda.cdm.metafields.ReferenceWithMetaParty
import org.joda.time.LocalDate
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.json.JsonObject

/*

{
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


*/
class PortfolioInstructions private constructor(
        val client:PortfolioInstructions.Client?,
        val security: Security?,
        val portfolioDate: ZonedDateTime?
){

    data class Client (
            val account: Account?
    )



    data class Builder(
            var instructions: JsonObject
            ){




        fun build() : PortfolioInstructions {

            val accJsonString = instructions.getJsonObject("PortfolioInstructions").getJsonObject("Client").getJsonObject("account").toString()
            val secJsonString = instructions.getJsonObject("PortfolioInstructions").getJsonObject("security").toString()
            val dateJsonString = instructions.getJsonObject("PortfolioInstructions").getString("PortfolioDate")


            val rosettaObjectMapper = RosettaObjectMapper.getDefaultRosettaObjectMapper()
            val account = rosettaObjectMapper.readValue<Account>(accJsonString,Account::class.java)
            val security = rosettaObjectMapper.readValue<Security>(secJsonString,Security::class.java)


            val df = SimpleDateFormat("yyyy-MM-dd")
            val date = df.parse(dateJsonString)


            val localDate = LocalDate.fromDateFields(date)


            val y = localDate.year
            val m = localDate.monthOfYear
            val d = localDate.dayOfMonth

            val dateTime = ZonedDateTime.of(y, m, d,
                    0, 0, 0, 0, ZoneId.systemDefault())






            return PortfolioInstructions(Client(account),security,dateTime)

        }

    }
}