package me.jscheah.jloopix.client;

class UnknownPacketException extends Exception {
    UnknownPacketException() {
        super();
    }

    UnknownPacketException(Exception e) {
        super(e);
    }
}
