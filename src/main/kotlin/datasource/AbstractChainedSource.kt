package com.r3.corda.evmbridge.evm.datasource

abstract class AbstractChainedSource<Key, Value, SourceKey, SourceValue> : Source<Key, Value> {
    private var source: Source<SourceKey, SourceValue>? = null
    private var flushSource: Boolean = false

    constructor()

    constructor(source: Source<SourceKey, SourceValue>) {
        this.source = source
    }

    protected fun setSource(src: Source<SourceKey, SourceValue>) {
        source = src
    }

    fun getSource(): Source<SourceKey, SourceValue>? {
        return source
    }

    fun setFlushSource(flushSource: Boolean) {
        this.flushSource = flushSource
    }

    @Synchronized
    override fun flush(): Boolean {
        var ret = flushImpl()
        if (flushSource) {
            ret = ret or (getSource()?.flush() ?: false)
        }
        return ret
    }

    protected abstract fun flushImpl(): Boolean
}

