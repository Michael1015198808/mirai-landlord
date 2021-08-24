package michael.landlord.main

import michael.landlord.LandlordConfig
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.Bot
import net.mamoe.mirai.message.code.MiraiCode.deserializeMiraiCode

var bot: Bot? = null
suspend fun CQ_sendGroupMsg(ac: Int, groupId: Long, str: String) {
    val last = str.split('\n').reduce {cumulation, line ->
        if (cumulation.length + line.length < LandlordConfig.length) {
            cumulation + "\n" + line
        } else {
            bot!!.getGroup(groupId)?.sendMessage(cumulation.deserializeMiraiCode())
            line
        }
    }
    bot!!.getGroup(groupId)?.sendMessage(last.deserializeMiraiCode())
}
suspend fun CQ_sendDiscussMsg(ac: Int, groupId: Long, str: String) {
    bot!!.getGroup(groupId)?.sendMessage(str.deserializeMiraiCode())
}
suspend fun CQ_sendPrivateMsg(ac: Int, memberId: Long, str: String) {
    fun getMember(memberId: Long): User? {
        bot?.getFriend(memberId)?.let { return it }
        bot?.getStranger(memberId)?.let { return it }
        bot?.groups?.forEach {
            it.getMember(memberId)?.let { return it}
        }
        return null
    }
    getMember(memberId)?.sendMessage(str.deserializeMiraiCode())
}

fun GetPrivateProfileInt(k: String, v: String, default: Long, path: String): Long {
    return default
}

fun GetPrivateProfileString(k: String, v: String, default: String, path: String): String {
    return default
}
fun WritePrivateProfileString(k: String, v: String, new: String, path: String): Boolean {
    assert(false)
    return false
}
