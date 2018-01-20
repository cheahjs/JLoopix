package me.jscheah.jloopix

data class Config(
        val EXP_PARAMS_LOOPS: Double,
        val EXP_PARAMS_DROP: Double,
        val EXP_PARAMS_PAYLOAD: Double,
        val EXP_PARAMS_DELAY: Double,
        val DATABASE_NAME: String,
        val TIME_PULL: Long,
        val MAX_DELAY_TIME: Long,
        val NOISE_LENGTH: Int,
        val MAX_RETRIEVE: Long,
        val DATA_DIR: String
)