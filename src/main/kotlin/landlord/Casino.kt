package michael.landlord.main

object Casino {
    val desks: MutableList<Desk> = mutableListOf()
    fun getDesk(deskNum: Long): Int {
        return this.desks.indexOfFirst { it.number == deskNum }
    }
    fun getOrCreatDesk(deskNum: Long): Desk {
        val deskIndex = getDesk(deskNum);
        if (deskIndex == -1) {//没有桌子
            val desk = Desk(deskNum)
            this.desks += desk;
            return desk
        } else {
            return this.desks[deskIndex];
        }
    }
    suspend fun game(subType: Boolean, deskNum: Long, playNum: Long, msgArray: String): Boolean {
        var msg = msgArray.trim().uppercase()

        var desk = this.getOrCreatDesk(deskNum);

        desk.subType = subType;

        if (playNum == 80000000L) {
            desk.msg += "匿名用户不能参加斗地主！"
            return false
        } else if (msg.startsWith("斗地主命令") || msg.startsWith("斗地主指令") || msg.startsWith("斗地主操作")) {
            desk.commandList();
        } else if (msg.startsWith("斗地主规则")) {
            desk.msg +=
                "斗地主规则：\n" +
                    "打牌一次奖励${CONFIG_PLAY_BONUS}分" +
                    "中途退出（弃牌）、挂机（抢地主、加倍${CONFIG_TIME_BOSS}秒，出牌${CONFIG_TIME_GAME}秒）倒扣${CONFIG_SURRENDER_PENALTY}分。\n" +
                    "每局游戏的标准分计算方法为：初始值${CONFIG_INIT_SCORE}分，" +
                    "最高最低分玩家的积分差额每有50分，" +
                    "标准分加${CONFIG_BOTTOM_SCORE }分，但标准分不会超过${CONFIG_TOP_SCORE}分。\n" +
                    "分数下限为负5亿，上限为正5亿。\n"
        } else if (msg.startsWith("斗地主")) {
            desk.msg += "斗地主${CONFIG_VERSION}\n" +
                "${Admin.readDataType()} ${Admin.readVersion()}\n" + // << L" CST"
                "源代码与更新履历：https://github.com/Michael1015198808/mirai-landlord\n" +
                "移植自（基于酷Q的C++斗地主）：https://github.com/doowzs/CQDouDiZhu\n" +
                "原作者与2.0.1源代码：https://github.com/lsjspl/CQDouDiZhu"
        } else if (msg.startsWith("上桌") || msg.startsWith("上座")  || msg.startsWith("上机") || msg.startsWith("打牌")) {
            desk.join(playNum);
        } else if ((desk.state >= STATE_READYTOGO) &&
            (msg.startsWith("出") || msg.startsWith("打"))) {//出牌阶段
            desk.play(playNum, msg)
        } else if ((desk.state >= STATE_READYTOGO) &&
            (msg.startsWith("过") || msg.startsWith("过牌") || msg.startsWith("不出") ||
                msg.startsWith("没有") || msg.startsWith("打不出") || msg.startsWith("要不起") ||
                msg.startsWith("不要") || msg.startsWith("PASS") )) {//跳过出牌阶段
            desk.discard(playNum);
        } else if (msg.startsWith("退桌") || msg.startsWith("下桌")
            || msg.startsWith("不玩了"))  {//结束游戏
            desk.exit(playNum);
        } else if (msg == "玩家列表") {
            desk.listPlayers(1);
        } else if (msg.startsWith("GO") || msg.startsWith("启动")) {
            if(desk.getPlayer(playNum) != -1) {
                desk.startGame();
            } else {
                desk.msg += "非玩家不能开始游戏！"
            }
        } else if ((msg.startsWith("抢") || msg.startsWith("要")) && desk.state == STATE_BOSSING) {
            desk.getBoss(playNum);
        } else if (msg.startsWith("不") && desk.state == STATE_BOSSING) {
            desk.dontBoss(playNum);
        } else if (msg.startsWith("加") && desk.state == STATE_MULTIPLING) {
            desk.getMultiple(playNum)
        } else if (msg.startsWith("不") && desk.state == STATE_MULTIPLING) {
            desk.dontMultiple(playNum);
        } else if (msg.startsWith("明牌")) {
            desk.openCard(playNum);
        } else if ((msg.startsWith("弃牌"))
            && desk.state >= STATE_BOSSING) {
            desk.surrender(playNum);
        }/*
        else if (msg == L"记牌器") {
                desk.msg << L"记牌器没做(好)呢！估计有生之年可以做好！";
        }
        else if (msg == L"我的信息") {
                desk.getPlayerInfo(playNum);
        } */
        else if (msg.startsWith("加入观战") || msg.startsWith("观战")) {
            desk.joinWatching(playNum)
        } else if (msg.startsWith("退出观战")) {
            desk.exitWatching(playNum)
        } /*
        //else if (msg.find(L"举报") == 0 || msg.find(L"挂机") == 0 || msg.find(L"AFK") == 0) {
        //	desk->AFKHandle(playNum);
        //} */
        else if (msg == "强制结束") {
            if (true || Admin.isAdmin(playNum)) {
                desk.msg += "管理员强制结束本桌游戏。\n"
                Casino.gameOver(deskNum)
                desks.remove(desk)
            } else {
                desk.msg += "你根本不是管理员！"
                desk.breakLine()
            }
        } else {
            // desk.msg += "命令解析失败！\n"
            // desk.msg += "输入\"强制结束\"以退出斗地主模式"
            return false
        }

        System.out.println("Finished with ${desk.msg}")
        desk.sendMsg(subType)
        desk.sendPlayerMsg()
        desk.sendWatcherMsg()
        return true;
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
        suspend { Util.sendGroupMsg(number, "游戏结束。") }
    }
    // void listDesks()
}
