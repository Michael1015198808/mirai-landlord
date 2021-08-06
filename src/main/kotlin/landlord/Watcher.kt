package michael.landlord.main

class Watcher(playNum: Long) {
    var msg: String = ""
    var number: Long = playNum
    suspend fun sendMsg() {
        if (msg.trim() != "") {
            Util.sendPrivateMsg(number, msg.trim())
        }
        msg = ""
    }

    fun breakLine() {
        msg += "\n"
    }

    fun at(playNum: Long): String {
        // TODO: Allow @people
        return "[mirai:at:$playNum]"
    }
}
