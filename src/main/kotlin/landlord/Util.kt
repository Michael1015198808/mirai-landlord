package michael.landlord.main

object Util {
    var AC: Int = 0
    // TODO: Replace calling to trim
    fun trim(s: String): String {
        return s.replace(" ", "")
    }
    fun testMsg(subType: Boolean, desknum: Long, playNum: Long, str: String) {
        // TODO: Just remove this line
        // val index: Int = casino.desks[0].currentPlayIndex;
        val index: Int = 0
        // Casino.game(subType, desknum, playNum + index, str);
    }
    suspend fun sendGroupMsg(groupid: Long, msg: String) {
        CQ_sendGroupMsg(Util.AC, groupid, msg);
    }
    suspend fun sendDiscussMsg(groupid: Long, msg: String) {
        CQ_sendDiscussMsg(Util.AC, groupid, msg);
    }

    suspend fun sendPrivateMsg(number: Long, msg: String) {
        CQ_sendPrivateMsg(Util.AC, number, msg);
    }
    fun mkdir() {
        // TODO: mkdir
        // CreateDirectory(CONFIG_DIR.c_str(), NULL);
    }

    fun findFlag(str: String): Int {
        return flag.indexOfFirst { it == str }
    }

    fun desc(a: Int, b: Int): Boolean {
        return a > b
    }

    fun asc(a: Int, b: Int): Boolean {
        return a > b
    }

    fun compareCard(carda: String, cardb: String): Boolean {
        return findFlag(carda) < findFlag(cardb)
    }

    fun setACUtil(ac: Int) {
        Util.AC = ac
    }
    fun strcat_tm(size: Long, now_time: Any): String {
        /*
        char tmp[20] = {0};

        tmp[0] = '0' + (now_time.tm_year % 100) / 10;
        tmp[1] = '0' + (now_time.tm_year % 100) % 10;
        tmp[2] = '0' + (now_time.tm_mon + 1) / 10;
        tmp[3] = '0' + (now_time.tm_mon + 1) % 10;
        tmp[4] = '0' + (now_time.tm_mday) / 10;
        tmp[5] = '0' + (now_time.tm_mday) % 10;
        tmp[6] = '0' + (now_time.tm_hour) / 10;
        tmp[7] = '0' + (now_time.tm_hour) % 10;
        tmp[8] = '0' + (now_time.tm_min) / 10;
        tmp[9] = '0' + (now_time.tm_min) % 10;
         */

        //wstring msg = L"我恨微软，当前时间：" + Util::string2wstring(tmp);
        //Util::sendPrivateMsg(Admin::readAdmin(), Util::wstring2string(msg).data());

        // TODO: return time
        return ""
    }
    fun stringToCards(raw_s: String): MutableList<String> {
        val s = raw_s.replace("王炸", "大王小王").replace(" ", "")
        var cards = mutableListOf<String>()
        var i: Int = 0
        while(i < s.length) {
            val argLen = if (s[i] in setOf<Char>('1', '小', '大')) 2 else 1
            cards += s.substring(i, i + argLen)
            i += argLen
        }
        return cards
    }
}
