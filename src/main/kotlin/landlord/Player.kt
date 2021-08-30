package michael.landlord.main

class Player(playerNum: Long) {
    var msg: String = ""
    var card: MutableList<String> = mutableListOf()
    var number: Long = playerNum
    var isReady: Boolean = false
    var isOpenCard: Boolean = false
    var isSurrender: Boolean = false
    var hasMultiplied: Boolean = false
    // 是否用过记牌器
    var counterUsed: Boolean = false
    suspend fun sendMsg() {
        if (msg != "") {
            Util.sendPrivateMsg(number, msg.trim())
            msg = ""
        }
    }

    fun breakLine() {
        msg += "\n"
    }

    fun handCards(): String {
        return this.card.joinToString("") { "[$it]" }
    }
}
