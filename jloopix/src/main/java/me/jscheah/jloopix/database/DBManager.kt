package me.jscheah.jloopix.database

import me.jscheah.jloopix.MixNode
import me.jscheah.jloopix.Provider
import me.jscheah.jloopix.User
import me.jscheah.sphinx.msgpack.Unpacker
import org.bouncycastle.math.ec.ECPoint
import java.sql.Connection
import java.sql.DriverManager

class DBManager (file: String) {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$file")

    fun selectAllMixNodes(): List<MixNode> {
        val statement = connection.createStatement()
        val result = statement.executeQuery("SELECT * FROM Mixnodes")
        return generateSequence {
            if (result.next())
                MixNode(result.getString("host"),
                        result.getShort("port"),
                        result.getString("name"),
                        blobToECPoint(result.getBytes("pubk")),
                        null,
                        result.getInt("groupId"))
            else null
        }.toList()
    }

    fun selectAllProviders(): List<Provider> {
        val statement = connection.createStatement()
        val result = statement.executeQuery("SELECT * FROM Providers")
        return generateSequence {
            if (result.next())
                Provider(result.getString("host"),
                        result.getShort("port"),
                        result.getString("name"),
                        blobToECPoint(result.getBytes("pubk")),
                        null)
            else null
        }.toList()
    }

    fun selectAllUsers(): List<User> {
        val statement = connection.createStatement()
        val result = statement.executeQuery("SELECT * FROM Users")
        return generateSequence {
            if (result.next())
                User(result.getString("host"),
                        result.getShort("port"),
                        result.getString("name"),
                        blobToECPoint(result.getBytes("pubk")),
                        null,
                        result.getString("provider"))
            else null
        }.toList()
    }

    private val providerCache: MutableMap<String, Provider> = mutableMapOf()

    fun getProviderFromName(name: String): Provider? {
        if (providerCache.containsKey(name)) {
            return providerCache[name]
        }
        val statement = connection.prepareStatement("SELECT * FROM Providers WHERE name = ?")
        statement.setString(1, name)
        val result = statement.executeQuery()
        if (result.next()) {
            val provider = Provider(result.getString("host"),
                    result.getShort("port"),
                    result.getString("name"),
                    blobToECPoint(result.getBytes("pubk")),
                    null)
            providerCache[name] = provider
            return provider
        }
        return null
    }

    private fun blobToECPoint(blob: ByteArray): ECPoint = Unpacker.getUnpacker(blob).unpackEcPoint()
}
