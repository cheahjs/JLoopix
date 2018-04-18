package me.jscheah.jloopix.client

import me.jscheah.jloopix.nodes.User

data class ClientMessage(
        val recipient: User,
        val data: ByteArray
)