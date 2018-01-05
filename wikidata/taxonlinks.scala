import org.json4s._
import org.json4s.jackson.JsonMethods._

@transient
lazy implicit val formats = DefaultFormats

def isTaxonInstance(json: JValue) = (json \ "claims" \ "P31" \\ "mainsnak" \ "datavalue" \ "value" \ "numeric-id").extract[Option[Int]] match {
  case Some(16521) => true
  case _ => false
}

def taxonItemId(json: JValue) = {
  if (isTaxonInstance(json)) {
    (json \ "id").extract[Option[String]]
  } else None
}

def idMapForTaxon(json: JValue): Seq[(String, String)] = {
  taxonItemId(json) match {
    case Some(id) => {
      val taxonMap = Seq(("NCBI", "P685"),
        ("ITIS", "P815"),
        ("EOL","P830"),
        ("FISHBASE","P938"),
        ("GBIF", "P846"),
        ("IF", "P1391"),
        ("INAT_TAXON", "P41964"),
        ("WORMS", "P850"))

      taxonMap.flatMap(entry =>  {
        (json \ "claims" \ entry._2 \\ "mainsnak" \ "datavalue" \ "value").extract[Option[String]] match {
          case Some(externalId) =>
            Some((s"$id", s"${entry._1}:$externalId"))
          case None => None
        }
      })
    }
    case None => Seq()
  }
}


val wikidata = spark.read.textFile("/guoda/data/source=wikidata/date=20171227/latest-all.json.bz2")

// select taxa chunks and turn into individual json objects
//
// taxa chunks are json object that mention taxon qualifier https://www.wikidata.org/wiki/Q16521
// turn into JSON Lines text format, also called newline-delimited JSON (see http://jsonlines.org/)
val taxaJsonString = wikidata.filter(_.contains("""Q16521"""")).map(_.stripLineEnd.replace(""",$""", ""))

import spark.implicits._

// extracts taxon links in form Seq((wikidata item, taxon id))
// for example (Q140, GBIF:5219404)
val taxonLinks = taxaJsonString.flatMap(line => idMapForTaxon(parse(line)))

// show first 10 links
taxonLinks.take(10)

// write all to parquet file for fast subsequent processing
//taxonLinks.write.parquet("/guoda/data/source=wikidata/date=20171227/taxonLinks.parquet")



