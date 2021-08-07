package michael.landlord.main

class Watchers() {
    var msg: String = ""
    var watcherIds: MutableSet<Long> = mutableSetOf()
    suspend fun sendMsg() {
        msg = msg.trim()
        if (msg != "") {
            watcherIds.forEach {
                Util.sendPrivateMsg(it, msg)
            }
        }
        msg = ""
    }

    fun breakLine() {
        msg += "\n"
    }

    fun at(playNum: Long): String {
        return "[mirai:at:$playNum]"
    }

    fun contains(playNum: Long): Boolean {
        return watcherIds.contains(playNum)
    }

    fun remove(playNum: Long): Boolean {
        return watcherIds.remove(playNum)
    }

    fun add(playNum: Long): Boolean {
        return watcherIds.add(playNum)
    }
}
