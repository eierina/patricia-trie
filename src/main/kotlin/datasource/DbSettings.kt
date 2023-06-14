package com.r3.corda.evmbridge.evm.datasource

class DbSettings private constructor() {
    var maxOpenFiles = 0
    var maxThreads = 0
    fun withMaxOpenFiles(maxOpenFiles: Int): DbSettings {
        this.maxOpenFiles = maxOpenFiles
        return this
    }

    fun withMaxThreads(maxThreads: Int): DbSettings {
        this.maxThreads = maxThreads
        return this
    }

    companion object {
        val DEFAULT: DbSettings = DbSettings()
            .withMaxThreads(1)
            .withMaxOpenFiles(32)

        fun newInstance(): DbSettings {
            val settings: DbSettings = DbSettings()
            settings.maxOpenFiles = DEFAULT.maxOpenFiles
            settings.maxThreads = DEFAULT.maxThreads
            return settings
        }
    }
}
