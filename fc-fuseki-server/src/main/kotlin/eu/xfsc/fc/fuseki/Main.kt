package eu.xfsc.fc.fuseki

import org.apache.jena.fuseki.main.FusekiServer
import org.apache.jena.tdb2.TDB2Factory

fun main(args: Array<String>) {
    val dir = "/fuseki_data"
    val ds = TDB2Factory.connectDataset(dir)
    val srv: FusekiServer = FusekiServer.create().add("/ds", ds).verbose(true).build()
    srv.start()
    Runtime.getRuntime().addShutdownHook(Thread {
        srv.stop()
    })
}