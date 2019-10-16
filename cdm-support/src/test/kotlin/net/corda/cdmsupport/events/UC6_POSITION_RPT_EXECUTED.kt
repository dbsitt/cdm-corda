package net.corda.cdmsupport.events

import net.corda.cdmsupport.eventparsing.readEventFromJson
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.functions.generateSettledPortfolioRpt
import net.corda.cdmsupport.functions.generateTradedPortfolioRpt
import net.corda.core.utilities.loggerFor
import org.junit.Test
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringReader
import javax.json.Json
import kotlin.test.assertNotNull

class UC6_POSITION_RPT_EXECUTED {

    @Test
    fun runSettledRptTest(){


        val logger = loggerFor <UC6_POSITION_RPT_SETTLED>()
        try {

            val instructions = Json.createReader(StringReader(readTextFromFile("/UC6_Position_Rpt/instructions/UC6_Portfolio_Instructions_20191016.json"))).readObject()
            val settlementEvent = readEventFromJson("/UC6_Position_Rpt/events/UC2_Allocation_Trade_AT1.json")
            val portfolio = generateTradedPortfolioRpt(instructions,settlementEvent)

            assertNotNull(portfolio)

            val json = serializeCdmObjectIntoJson(portfolio)

            logger.debug(json)

            //generate report to file system
            val output = File("UC6_CLIENT_PORTFOLIO_RPT_20191016.json")

            val writer = PrintWriter(FileWriter(output))

            writer.println(json)

            writer.close()

        }catch(ex:Exception){
            logger.debug("Exception - ",ex)
        }

    }
}