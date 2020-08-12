package com.mongodb.alliance.model

import org.drinkless.td.libcore.telegram.TdApi
import org.drinkless.td.libcore.telegram.Client

public class OrderedChat internal constructor(val chatId: Long, position: Int) :
    Comparable<OrderedChat?> {
    val position: Int
    override operator fun compareTo(other: OrderedChat?): Int {
        if (position !== other.position) {
            if (other != null) {
                return if (other.position < position) -1 else 1
            }
        }
        if (other != null) {
            return if (chatId != other.chatId) {
                if (other.chatId < chatId) -1 else 1
            } else 0
        }
    }

    override fun equals(obj: Any?): Boolean {
        val o = obj as OrderedChat?
        return chatId == o!!.chatId && position === o.position
    }

    init {
        this.position = position
    }

}