package net.corda.cdmsupport.events

import com.regnosys.rosetta.common.serialisation.RosettaObjectMapper
import groovy.json.JsonBuilder
import net.corda.cdmsupport.eventparsing.readEventFromJson
import net.corda.cdmsupport.eventparsing.readTextFromFile
import net.corda.cdmsupport.eventparsing.serializeCdmObjectIntoJson
import net.corda.cdmsupport.functions.generateSettledPortfolioRpt
import org.isda.cdm.*
import org.isda.cdm.metafields.*
import org.junit.Test
import java.io.FileReader
import java.io.StringReader
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.json.Json
import kotlin.test.assertNotNull


class PortfolioReportSettledTest {



    @Test
    fun runTest(){





        try {



            val instructions = Json.createReader(StringReader(readTextFromFile("/settlement/UC6_Portfolio_Instructions_20191017.json"))).readObject()

            val settlementEvent = readEventFromJson("/settlement/UC5_Settlement_AT1.2.json")

            val portfolio = generateSettledPortfolioRpt(instructions,settlementEvent)

            val json = serializeCdmObjectIntoJson(portfolio)

            //println(json)
            assertNotNull(json)


        }catch(ex:Exception){
            ex.printStackTrace(System.out)
        }

    }
}