package michael.landlord.main

import michael.landlord.PluginMain
import michael.landlord.PluginMain.globalStatisticsData
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Group

object Casino {
    val desks: MutableList<Desk> = mutableListOf()
    fun getDesk(deskNum: Long): Int {
        return this.desks.indexOfFirst { it.number == deskNum }
    }
    fun getOrCreatDesk(group: Group): Desk {
        val groupId = group.id
        val deskIndex = getDesk(groupId);
        if (deskIndex == -1) {//没有桌子
            val desk = Desk(groupId, group)
            this.desks += desk;
            return desk
        } else {
            return this.desks[deskIndex];
        }
    }

    suspend fun game_manage(playNum: Long, raw_msg: String): Boolean {
        val msg = raw_msg.trim().uppercase()

        var result: Boolean = false
        if (msg == "我是管理") {
            result = Admin.IAmAdmin(playNum)
        } /* else if (msg == L"重置斗地主" || msg == L"初始化斗地主") {
            result = Admin::resetGame(playNum);
        } else if (msg.find(L"改变数据") == 0) {
            result = Admin::writeDataType();
            Admin::getPlayerInfo(Admin::readAdmin());
        } else if (regex_match(msg, allotReg)) {
            result = Admin::allotScoreTo(msg, playNum);
        } else if (regex_match(msg, allotReg2)) {
            result = Admin::allotScoreTo2(msg, playNum);
        } else if (msg.find(L"结束游戏") == 0 || msg.find(L"停止游戏") == 0) {//结束游戏
            result = Admin::gameOver(msg, playNum);
        } else if (msg == L"我的信息") {
            Admin::getPlayerInfo(playNum);
            return false;
        } else if (regex_match(msg, getInfoReg)) {
            //查询指定玩家积分
            int score;
            int64_t playerNum;

            wsmatch mr;
            wstring::const_iterator src_it = msg.begin(); // 获取起始位置
            wstring::const_iterator src_end = msg.end(); // 获取结束位置
            regex_search(src_it, src_end, mr, numberReg);
            wstringstream ss;
            ss << mr[0].str();
            ss >> playerNum;
            ss.str(L"");

            Admin::getPlayerInfo(playNum);
            return false;
        } else if (msg == L"备份数据") {
            result = Admin::backupData(playNum);
        } else {
            return false;
        }
         */

        Util.sendPrivateMsg(playNum, if(result) "操作成功，尊贵的管理员" else "非常抱歉，操作失败")
        Util.sendPrivateMsg(playNum, "该部分代码逻辑尚未移植(michael.mirai.plugin.landlord.Desks)")
        return true
    }
    fun gameOver(number: Long) {
        val index = Casino.getDesk(number);
        if (index == -1) {
            return;
        }

        //销毁挂机检测程序
        Casino.desks[index].state = STATE_OVER
        // casino.desks[index].counter.join()

        Casino.desks.removeAt(index)
        //更新数据库版本
        //Admin::writeVersion();
    }
    // void listDesks()
}
