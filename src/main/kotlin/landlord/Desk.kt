package michael.landlord.main

import kotlin.math.min
import kotlin.random.Random

class Desk(number: Long) {
    var turn: Int = 0
    var multiple: Long = 1 // 分数倍率
    var basic: Long = CONFIG_INIT_SCORE
    var lastTime: Long = 0
    var number: Long = number // 桌号？
    var cards: MutableList<String> = cardDest.toMutableList()
    @Volatile
    var players: MutableList<Player> = mutableListOf()
    var watchers: MutableList<Watcher> = mutableListOf()
    // TODO: 倒计时器
    // thread *counter;	//倒计时器

    var whoIsWinner: Int = 0
    var state: Int = STATE_WAIT
    var lastPlayIndex: Int = -1//当前谁出得牌
    var currentPlayIndex: Int = -1//该谁出牌
    // TODO: 改名为landlord
    var bossIndex: Int = -1//谁是地主
    var bossHasMultipled: Boolean = false
    var isSecondCallForBoss: Boolean = false//第二次叫地主
    var warningSent: Boolean = false//倒计时警告消息已发送

    var subType: Boolean = false//存储消息类型

    var bossCount: Int = 0//记录出牌次数，检测春天
    var farmCount: Int = 0

    var lastCard: List<String> = listOf() //上位玩家的牌
    var lastCardType: String = "" //上位玩家得牌类
    var lastWeights: List<Int> = listOf() //上位玩家的牌的权重

    var msg: String = ""

    fun at(playNum: Long): String {
        // TODO: Allow @people
        return "@$playNum";
    }

    fun breakLine() {
        msg += "\n"
    }

    fun getPlayer(number: Long): Int {
        return players.indexOfFirst { it.number == number }
    }

    fun getWatcher(number: Long): Int {
        return watchers.indexOfFirst { it.number == number }
    }

    fun listPlayers(type: Int): String {
        val hasType: Boolean = type.and(1) == 1
        if(hasType && state < STATE_READYTOGO) {
            System.err.println("type=$type but state < STATE_GAMING")
        }
        val hasWin: Boolean  = type.and(2) == 1

        val score = basic * multiple
        val halfScore = score / 2
        // TODO: Test mutablelist's speed and String's speend
        var ret: MutableList<String> = mutableListOf()

        if (players.size < 3) {
            ret += "游戏尚未开始，玩家列表：\n"
        } else {
            ret += "本局积分：${basic*multiple}\n"
            ret += "出牌次数：$turn\n"
        }
        for((i, player) in players.withIndex()) {
            ret += "${i+1}号玩家："
            if (state >= STATE_READYTOGO) {
                ret += "[${if(i == bossIndex) "地主" else "农民"}]"
            }

            ret += at(player.number)
            if (hasWin) {
                val farm_flag: Boolean = (whoIsWinner == 2) //如果是农民赢了
                var add_score: Long
                var win_flag: Boolean? = null
                if (player.isSurrender) {
                    add_score = -CONFIG_SURRENDER_PENALTY
                    ret += "[投降，${add_score}分]"
                } else if ((i == bossIndex).xor(farm_flag)) {
                    add_score = if(farm_flag) -score else -halfScore
                    ret += "[失败，${add_score}分]"

                    ret += "\n剩余手牌："
                    ret += listCardsOnDesk(player)
                } else {
                    add_score = if(farm_flag) halfScore else score
                    ret += "[胜利，+${add_score}分"

                    //如果还有牌，就公开手牌
                    if (player.card.size > 0) {
                        ret += "\n剩余手牌："
                        ret += listCardsOnDesk(player)
                    }
                }
                assert(win_flag != null)
                assert(add_score != 0L)
                Admin.addScore(player.number, add_score)
                if(win_flag != null) {
                    if(win_flag) Admin.addWin(player.number) else Admin.addLose(player.number)
                }
            } else {
                ret += "：${player.card.size}张手牌"
            }
            ret += "\n"
        }
        return ret.joinToString("")
    }

    fun isCanWin(cardCount: Int, weights: List<Int>, type: String): Boolean {
        if (type == "" || lastCardType == "王炸") {
            return false;
        }
        if (lastCardType == "") {
            return true;
        }
        if (type == "王炸") {
            return true;
        }
        if (type == "炸弹" && type != lastCardType) {
            return true;
        }
        if (type == lastCardType && cardCount == lastCard.size) {
            return weights.zip(lastWeights).any() { (a, b) ->
                a > b
            }
        }
        return false;
    }

    fun getMycardType(list: MutableList<String>, weights: MutableList<Int>): String {
        val cardCount = list.size
        list.sortBy { Util.findFlag(it) }

        if (cardCount == 2 && Util.findFlag(list[0]) + Util.findFlag(list[1]) == 27) {//王炸
            return "王炸"
        }

        val cards: MutableList<String> = mutableListOf()
        val counts: MutableList<Int> = mutableListOf()

        val no2InCards: Boolean = list.any { it == "2"}

        for (card in list) {
            val index: Int = cards.indexOf(card)
            if (index == -1) {
                cards += card
                counts += 1
            } else {
                ++counts[index]
            }
        }

        var max = counts.maxOrNull()
        var min = counts.minOrNull()
        var cardGroupCout = cards.size

        var tmpCount: MutableList<Int> = counts.toMutableList()
        tmpCount.sortDescending()
        if (cardCount == 1) {//单牌
            weights += Util.findFlag(cards[0])
            return "单牌"
        }
        if (cardCount == 2 && max == 2) {//对子
            weights += Util.findFlag(cards[0])
            return "对子"
        }
        if (cardCount == 3 && max == 3) {//三张
            weights += Util.findFlag(cards[0])
            return "三张"
        }
        if (cardCount == 4 && max == 4) {//炸弹
            weights += Util.findFlag(cards[0])
            return "炸弹"
        }

        if (cardCount == 4 && max == 3) {//3带1
            for (tmp in tmpCount) {
                for (m in 0..counts.size-1) {
                    if (counts[m] == tmp) {
                        weights += Util.findFlag(cards[m])
                        counts[m] = -1
                    }
                }
            }
            return "3带1"
        }

        if (cardCount == 5 && max == 3 && min == 2) {//3带2
            for (tmp in tmpCount) {
                for (m in 0..counts.size-1) {
                    if (counts[m] == tmp) {
                        weights += Util.findFlag(cards[m])
                        counts[m] = -1
                    }
                }
            }
            return "3带2";
        }

        // BUG: AAAABB?
        if (cardCount == 6 && max == 4) {//4带2
            for (tmp in tmpCount) {
                for (m in 0..counts.size-1) {
                    if (counts[m] == tmp) {
                        weights += Util.findFlag(cards[m])
                        counts[m] = -1
                    }
                }
            }
            return "4带2";
        }

        if (cardGroupCout > 2 && max == 2 && min == 2
            && Util.findFlag(cards[0]) == Util.findFlag(cards[cardGroupCout - 1]) - cardGroupCout + 1
            && Util.findFlag(cards[cardGroupCout - 1]) < 13
            && no2InCards	//连对不能有2
        ) {//连对
            for (i in 0..tmpCount.size - 1) {
                val tmp = tmpCount[i]
                for (m in 0..counts.size - 1) {
                    if (counts[m] == tmp) {
                        weights += Util.findFlag(cards[m])
                        counts[m] = -1
                    }
                }
            }

            return "连对"
        }

        assert(false)
        return ""
        /*
        //for (unsigned i = 0; i < cardGroupCout; i++) {
        //	if (cards[cardGroupCout] == L"2") {
        //		no2InCards = false;
        //	}
        //}

        if (cardGroupCout > 4 && max == 1 && min == 1
            && Util::findFlag(cards[0]) == Util::findFlag(cards[cardGroupCout - 1]) - cardGroupCout + 1
            && Util::findFlag(cards[cardGroupCout - 1]) < 13
            && no2InCards	//三人斗地主顺子不能带2
        ) {//顺子
            for (unsigned i = 0; i < tmpCount.size(); i++) {
                int tmp = tmpCount[i];
                for (unsigned m = 0; m < counts.size(); m++) {
                if (counts[m] == tmp) {
                        weights->push_back(Util::findFlag(cards[m]));
                    counts[m] = -1;
                }
            }
            }

            return L"顺子";
        }

        //飞机
        int  planeCount = 0;
        for (unsigned i = 0; i < counts.size(); i++) {
            if (counts[i] >= 3) {
                planeCount++;
            }
        }
        if (planeCount > 1) {
            wstring tmp;
            if (cardCount == planeCount * 3) {
                tmp = L"飞机";
            }
            else if (cardCount == planeCount * 4) {
                tmp = L"飞机带翅膀";
            }
            else if (cardCount == planeCount * 5 && min == 2) {
                tmp = L"飞机带双翅膀";
            }

            for (int i = 0; i < planeCount; i++) {
                int tmp = tmpCount[i];
                for (unsigned m = 0; m < counts.size(); m++) {
                if (counts[m] == tmp) {
                        weights->push_back(Util::findFlag(cards[m]));
                    counts[m] = -1;
                }
            }
            }

            sort(weights->begin(), weights->end(), Util::desc);

            int weightscount = weights->size();

            if (weights->at(0) - weightscount + 1 != weights->at(weightscount - 1)) {
                return L"";
            }

            return tmp;
        }
        return L"";
         */
    }

    suspend fun sendMsg(subType: Boolean) {
        if (subType) {
            if(msg.trim() != "") {
                Util.sendGroupMsg(number, msg.trim());
            }
        } else {
            if(msg.trim() != "") {
                Util.sendDiscussMsg(number, msg.trim());
            }
        }
        msg = ""
    }

    suspend fun sendPlayerMsg() {
        players.forEach { it.sendMsg() }
    }

    suspend fun sendWatcherMsg() {
        watchers.forEach{ it.sendMsg() }
    }

    fun shuffle() {
        java.util.Collections.shuffle(cards)
    }

    fun createBoss() {
        state = STATE_BOSSING

        //记录时间
        // time_t rawtime;
        // lastTime = time(&rawtime);
        //防止bug
        warningSent = false

        msg +=
            "下面进入抢地主环节，倒计时开始。\n" +
                "--------------------------\n"

        val index = Random.nextInt(0, 3)

        bossIndex = index
        currentPlayIndex = index
        msg += at(players[index].number)
        breakLine()
        msg += "你是否要抢地主？\n请用[抢]或[不(抢)]来回答。\n"
        //战况播报
        sendWatchingMsg_Start()
    }

    fun getBoss(playerNum: Long) {
        val index = getPlayer(playerNum);
        if (state == STATE_BOSSING && currentPlayIndex == index) {
            //记录时间
            // time_t rawtime;
            // lastTime = time(&rawtime);
            //防止提示后抢地主出现bug
            warningSent = false

            bossIndex = index
            currentPlayIndex = index
            lastPlayIndex = index
            sendBossCard()

            //进入加倍环节
            state = STATE_MULTIPLING
            multipleChoice()
        }
    }

    fun dontBoss(playerNum: Long) {
        val index = getPlayer(playerNum)
        if (state == STATE_BOSSING && currentPlayIndex == index) {
            setNextPlayerIndex()

            //记录时间
            // time_t rawtime;
            // lastTime = time(&rawtime)
            //防止提示后不抢出现bug
            warningSent = false

            if (currentPlayIndex == bossIndex && isSecondCallForBoss) {
                msg += "第2次抢地主失败，"
                sendBossCard()
                state = STATE_MULTIPLING
                multipleChoice()
                return
            }
            else if (currentPlayIndex == bossIndex) {
                msg +=
                    "第1次抢地主失败，重新发牌。\n" +
                        "------------------------\n"
                isSecondCallForBoss = true
                shuffle();
                deal();
                createBoss();
                return;
            }
            else {
                msg += at(players[index].number);
                msg += "不抢地主。";
                breakLine();
                msg += "---------------";
                breakLine();
                msg += at(players[currentPlayIndex].number);
                breakLine();
                msg += "你是否要抢地主？";
                breakLine();
                msg += "请用[抢]或[不(抢)]来回答。";
                breakLine();
            }
        }
    }


    fun sendBossCard() {
        val playerBoss: Player = players[bossIndex]

        msg += at(playerBoss.number)
        msg += "是地主，底牌是：";
        msg +=
            "[" + cards[53] + "]" +
                "[" + cards[52] + "]" +
                "[" + cards[51] + "]。"
        breakLine();
        msg += "---------------"
        breakLine();

        for (i in 0..2) {
            playerBoss.card += cards[53 - i]
        }

        playerBoss.card.sortBy { Util.findFlag(it) }

        //playerBoss->msg << L"你是地主，收到底牌：";
        //playerBoss->breakLine();
        for(card in playerBoss.card) {
            playerBoss.msg += "[" + card + "]"
        }
        playerBoss.breakLine()

        //这里的状态声明移动到了加倍(dontMultiple)的最后一个人语句哪里。
    }

    fun multipleChoice() {
        //记录时间
        // time_t rawtime;
        // lastTime = time(&rawtime);
        //防止bug
        warningSent = false;

        msg += "抢地主环节结束，下面进入加倍环节。\n"
        msg += "---------------\n"
        msg += at(players[bossIndex].number)
        breakLine()
        msg += "你是否要加倍？\n"
        msg += "请用[加]或[不(加)]来回答。\n"
    }
    fun getMultiple(playerNum: Long) {
        val index = getPlayer(playerNum)
        if (state == STATE_MULTIPLING && currentPlayIndex == index) {
            multiple += 1;

            //记录时间
            // time_t rawtime;
            // lastTime = time(&rawtime);
            //防止提示后加倍出现bug
            warningSent = false;

            setNextPlayerIndex();

            if (currentPlayIndex == bossIndex && bossHasMultipled) {
                msg += at(players[index].number);
                msg += "要加倍。\n"
                msg += "本局积分：${basic*multiple}\n"
                msg += "---------------\n"

                state = STATE_READYTOGO;

                msg += "加倍环节结束，斗地主正式开始。\n"
                msg += "---------------\n"
                //msg << L"第" << turn + 1 << L"回合：剩余手牌数：";
                //breakLine();

                msg += listPlayers(1)

                msg += "请地主";
                msg += at(players[bossIndex].number)
                msg += "先出牌。\n"
                //战况播报
                sendWatchingMsg_Start()
            }
            else {
                bossHasMultipled = true

                msg += at(players[index].number)
                msg += "要加倍。\n"
                msg += "本局积分：${basic*multiple}"
                this.msg += "\n---------------\n";
                msg += this.at(players[currentPlayIndex].number);
                this.msg += "你是否要加倍？\n"
                this.msg += "请用[加]或[不(加)]来回答。\n"
            }
        }
    }

    fun dontMultiple(playerNum: Long) {
        val index: Int = getPlayer(playerNum);
        if (this.state == STATE_MULTIPLING && this.currentPlayIndex == index) {
            this.setNextPlayerIndex();

            //记录时间
            // time_t rawtime;
            // this.lastTime = time(&rawtime);
            //防止提示后不加倍出现bug
            this.warningSent = false;

            if (this.currentPlayIndex == this.bossIndex && bossHasMultipled) {
                msg += this.at(this.players[index].number);
                this.msg += "不要加倍。\n"
                this.msg += "---------------\n"

                this.state = STATE_READYTOGO

                this.msg += "加倍环节结束，斗地主正式开始。\n"
                this.msg += "---------------\n"
                //this.msg << L"第" << this.turn + 1 << L"回合：";
                //this.breakLine();
                this.msg += "本局积分：${this.basic*this.multiple}\n"
                this.msg += "剩余手牌数：\n"
                msg += this.listPlayers(1)
                this.breakLine();
                this.msg += "请地主"
                msg += this.at(players[this.bossIndex].number)
                this.msg += "先出牌。\n"
                //战况播报
                this.sendWatchingMsg_Start();
            }
            else {
                bossHasMultipled = true;

                msg += at(players[index].number)
                msg += "不要加倍。\n"
                msg += "---------------\n"
                msg += at(players[currentPlayIndex].number);
                breakLine();
                msg += "你是否要加倍？\n"
                msg += "请用[加]或[不(加)]来回答。\n"
            }
        }
    }

    fun play(playNum: Long, raw_msg: String) {
        val playIndex: Int = this.getPlayer(playNum)
        val msg = raw_msg.replace(" ", "")
        val length = msg.length

        if (playIndex == -1) {
            this.msg += "你不是玩家！"
            return
        }
        if (playIndex != this.currentPlayIndex) {
            this.msg += "还没轮到你出牌"
            return
        }
        if((!(this.state == STATE_GAMING && this.turn > 0)
                && !(this.state == STATE_READYTOGO && this.turn == 0))) {
            this.msg += "游戏尚未开始！"
            return
        }
        if(length < 2) {
            this.msg += "请跟上你要打出的牌，如\"出33344\"或\"打34567\""
            return
        }

        var msglist = mutableListOf<String>()
        // BUG: starts from 0?
        var i: Int = 1
        while(i < length) {
            val arg_len = if (msg[i] in setOf<Char>('1', '小', '大')) 2 else 1
            msglist += msg.substring(i, i + arg_len)
            i += arg_len
        }
        play_real(playIndex, msglist)
    }

    fun play_real(playIndex: Int, list: MutableList<String>) {
        val player: Player = this.players[playIndex]
        var mycardTmp: MutableList<String> = player.card.toMutableList()

        val cardCount: Int = list.size

        for (card in list) {
            if (!mycardTmp.remove(card)) {
                this.msg += this.at(this.players[currentPlayIndex].number)
                this.breakLine()
                this.msg += "真丢人，你就没有你要出的牌，会不会玩？\n"
                return
            }
        }

        var weights: MutableList<Int> = mutableListOf()
        val type = getMycardType(list, weights);

        val isCanWin: Boolean = this.isCanWin(cardCount, weights, type)

        if (isCanWin) {
            if (this.turn == 0) {
                this.state = STATE_GAMING
            }

            //只有合法的出牌才能记录
            //处理出牌次数
            if (currentPlayIndex == this.bossIndex) {
                this.bossCount++
            } else {
                this.farmCount++
            }

            //记录出牌时间
            // time_t rawtime
            // this.lastTime = time(&rawtime)
            //防止提示后出牌出现bug
            this.warningSent = false

            player.card = mycardTmp
            this.lastWeights = weights
            this.lastCard = list
            this.lastCardType = type
            this.lastPlayIndex = this.currentPlayIndex
            this.turn++

            //处理积分
            if (type == "王炸") {
                this.multiple += 2;

                this.msg += "打出王炸，积分倍数+2\n"
                // TODO: check copy-and-paste
                this.msg += "本局积分：${this.basic*this.multiple}\n"
                this.msg += "---------------\n"
            } else if (type == "炸弹") {
                this.multiple += 1;

                this.msg += "打出炸弹，积分倍数+1\n"
                this.msg += "本局积分：${this.basic*this.multiple}\n"
                this.msg += "---------------\n"
            }

            if (mycardTmp.size == 0) {//赢了。
                this.whoIsWinner = if (this.bossIndex == this.currentPlayIndex) 1 else 2

                this.sendWatchingMsg_Over()

                this.msg += "斗地主游戏结束，"
                this.msg += if(this.whoIsWinner == 1) "地主赢了" else "农民赢了"
                this.breakLine()

                if (this.farmCount == 0 && this.whoIsWinner == 1) {
                    this.multiple *= 2;
                    this.msg += "---------------\n"
                    this.msg += "本局出现春天，积分倍数x2\n"
                    this.msg += "本局积分：${this.basic*this.multiple}\n"
                } else if (this.bossCount == 1 && this.whoIsWinner == 2) {
                    this.multiple *= 2;
                    this.msg += "---------------\n"
                    this.msg += "本局出现反春天，积分倍数x2\n"
                    this.msg += "本局积分：${this.basic*this.multiple}\n"
                }


                this.msg += "---------------\n"
                this.msg += "分数结算：\n"
                this.msg += this.listPlayers(3);

                Casino.gameOver(this.number);
                return
            }

            player.listCards()

            if (player.isOpenCard) {
                this.msg += this.at(player.number)
                this.msg += "明牌："
                this.msg += this.listCardsOnDesk(player)
                this.breakLine()
                this.msg += "---------------\n"
            }

            if (player.card.size < 3) {
                this.msg += "红色警报！红色警报！\n"
                this.msg += this.at(player.number)
                this.msg += "仅剩下${player.card.size}张牌！\n"
                this.msg += "---------------\n"
            }

            //this.msg << L"上回合";
            this.msg += this.at(this.players[currentPlayIndex].number)
            this.msg += "打出" + this.lastCardType
            for(card in this.lastCard){
                this.msg += "[$card]"
            }
            this.breakLine()

            //观战播报，必须先转发战况再设置下一位玩家，否则玩家信息错误
            this.sendWatchingMsg_Play()

            this.setNextPlayerIndex()

            this.msg += "---------------\n"
            //this.msg << L"第" << this.turn + 1 << L"回合：";
            //this.breakLine();
            this.msg += "本局积分：${this.basic*this.multiple}\n"
            this.msg += "剩余手牌数：\n"
            this.msg += this.listPlayers(1)
            this.msg += "现在轮到"
            this.msg += this.at(this.players[this.currentPlayIndex].number)
            this.msg += "出牌。\n"
        }
        else {
            this.msg += this.at(this.players[this.currentPlayIndex].number)
            this.breakLine()
            this.msg += "傻逼网友，打的什么几把玩意！学会出牌再打！\n"
        }
    }

    fun discard(playNum: Long) {
        if (this.currentPlayIndex != this.getPlayer(playNum) || this.state != STATE_GAMING) {
            return
        }

        if (this.currentPlayIndex == this.lastPlayIndex) {
            this.msg += "过过过过你妹，会不会玩，你不能过牌了，丢人！"
            return
        }

        //记录过牌时间
        // time_t rawtime;
        // this.lastTime = time(&rawtime);
        //防止提示后过牌出现bug
        this.warningSent = false;

        this.msg += this.at(playNum)
        this.msg += "过牌，"

        this.setNextPlayerIndex();

        this.msg += "现在轮到";
        this.msg += this.at(this.players[this.currentPlayIndex].number)
        this.msg += "出牌。\n"

        //观战播报
        this.sendWatchingMsg_Pass(playNum);
    }

    fun surrender(playNum: Long) {
        val index = this.getPlayer(playNum)
        if (index == -1 || this.players[index].isSurrender) {
            return
        }
        if (this.state != STATE_GAMING) {
            this.msg += this.at(playNum)
            this.msg += "当前游戏状态无法弃牌（投降）！任意出牌后方可弃牌。"
            return;
        }

        //记录弃牌时间
        // time_t rawtime;
        // this.lastTime = time(&rawtime);
        //防止提示后弃牌出现bug
        this.warningSent = false;

        val player = this.players[index]

        player.isSurrender = true;

        if (index == this.bossIndex) {
            this.whoIsWinner = 2;//农民赢
        } else {
            if(players.sumOf { if(it.isSurrender) 1 as Int else 0 } == 2) {
                this.whoIsWinner = 1
            }
        }

        if (this.whoIsWinner > 0) {
            this.sendWatchingMsg_Over();

            this.msg += "斗地主游戏结束，";
            this.msg += if(this.whoIsWinner == 1) "地主赢了" else "农民赢了"
            this.breakLine()

            //弃牌不检测春天

            this.msg += "---------------\n"
            this.msg += "分数结算：\n"
            this.msg += this.listPlayers(3);

            Casino.gameOver(this.number);
            return;
        }

        if (this.currentPlayIndex == index) {
            this.msg += this.at(playNum)
            this.msg += "弃牌，"

            this.setNextPlayerIndex()

            this.msg += "现在轮到"
            this.msg += this.at(this.players[this.currentPlayIndex].number)
            this.msg += "出牌。"
            this.breakLine()
        } else {
            this.msg += this.at(playNum)
            this.msg += "弃牌。"
            this.breakLine()
        }

        //观战播报
        this.sendWatchingMsg_Surrender(playNum);
    }

    fun openCard(playNum: Long) {
        val index = this.getPlayer(playNum);

        if (index == -1) {
            this.msg += "你不是玩家，不能明牌！"
            suspend { this.sendMsg(true) }
            return;
        }
        if (this.state > STATE_READYTOGO || this.state < STATE_START) {
            this.msg += this.at(playNum)
            this.breakLine()
            this.msg += "明牌只能在准备阶段使用！"
            return
        }
        val player = this.players[index];

        if (!player.isOpenCard) {
            player.isOpenCard = true;
            this.multiple += 2;
        }

        this.msg += this.at(playNum);
        this.msg += "明牌，积分倍数+2。\n"

        this.msg += this.listCardsOnDesk(player)
        this.breakLine()

        this.msg += "---------------\n"
        this.msg += "本局积分：${this.basic*this.multiple}"
    }

    fun getPlayerInfo(playNum: Long): String {
        var ret = ""
        ret += "${Admin.readDataType()} ${Admin.readVersion()}\n"
        ret += this.at(playNum);
        ret += "：";
        //this.breakLine();
        ret += "${Admin.readWin(playNum)}胜${Admin.readLose(playNum)}负，"
        ret += "积分${this.readScore(playNum)}。\n"
        return ret
    }

    fun getScore(playNum: Long) {
        this.msg += this.at(playNum)
        this.breakLine()
        if (Admin.getScore(playNum)) {
            this.msg += "这是今天的200点积分，祝你玩♂得开心！\n";
            this.msg += "你现在的积分总额为${Admin.readScore(playNum)}，"
        }
        else {
            this.msg += "你今日已经领取过积分！\n";
        }
        this.msg += "获取更多积分请明天再来或是和管理员";
        this.msg += this.at(Admin.readAdmin());
        this.msg += "进行py交易！\n";
    }

    fun readScore(playNum: Long): Long {
        return Admin.readScore(playNum) - 500000000L
    }

    fun readSendScore(playNum: Long) {
        this.msg += (Admin.readScore(playNum) - 500000000).toString()
    }

    fun setNextPlayerIndex() {
        this.currentPlayIndex = (this.currentPlayIndex + 1) % 3

        if (this.currentPlayIndex == this.lastPlayIndex) {
            this.lastCard = listOf()
            this.lastWeights = listOf()
            this.lastCardType = ""
        }

        //如果下一个该出牌的玩家正好弃牌了 则重新set下一位玩家
        //由于不可能大于2个人弃牌 所以下一个人一定没有弃牌
        if (this.players[this.currentPlayIndex].isSurrender) {
            this.setNextPlayerIndex()
        }
    }

    fun deal() {
        var j = 0
        for(player in players) {
            //第二次发牌时需要消除所有已经发到的牌
            player.card = cards.subList(j * 17, j * 17 + 17).sortedBy { Util.findFlag(it) }.toMutableList()

            for(card in player.card) {
                player.msg += "[$card]"
            }
            player.breakLine()
            ++j
        }
    }

    fun join(playNum: Long) {
        if (this.getPlayer(playNum) != -1) {
            this.msg += this.at(playNum);
            this.breakLine();
            this.msg += "你已经加入游戏，不能重复加入！\n"
            return;
        }

        if (this.players.size == 3) {
            this.msg += this.at(playNum);
            this.breakLine();
            this.msg += "很遗憾，人数已满！\n"
            this.msg += "但你可以[加入观战]！\n"
            //this->joinWatching(playNum); 不再自动加入
            return
        }

        //if (Admin::readScore(playNum) < 1) {
        //this->at(playNum);
        //this->breakLine();
        //this->msg += "你的积分已经输光了，祝你在游戏中弯道超车！";
        //this->breakLine();
        //this->msg += "系统自动为您尝试获取每日积分：";
        //this->breakLine();
        //this->msg += "---------------";
        //this->breakLine();
        //this->getScore(playNum);
        //获取积分的最后有换行，不需要 this->breakLine();
        //this->msg += "---------------";
        //this->breakLine();

        //如果领取失败，赠送5分
        //if (Admin::readScore(playNum) < 1) {
        //this->msg += "系统赠送你5点积分，祝你弯道超车！";
        //Admin::addScore(playNum, 5);
        //return;
        //}
        //}

        val player = Player(playNum)
        this.players += player

        this.msg += this.at(playNum)
        this.breakLine();
        this.msg += "加入成功，已有玩家${this.players.size}人，分别是：\n"
        this.msg += this.playersInfo()

        if (Admin.readScore(playNum) <= 0) {
            this.msg += "---------------"
            this.breakLine();
            this.msg += this.at(playNum);
            this.breakLine();
            this.msg += "你的积分为";
            this.readSendScore(playNum);
            this.msg += "，"; //在这里van游戏可能太♂弱而受到挫折！";
            //this->breakLine();
            this.msg += "请多多练♂习以提高你的牌技，祝你弯道超车！"
            this.breakLine();
        }


        if (this.players.size == 3) {
            this.breakLine();
            this.msg += "人数已满，";
            this.msg += "请输入[启动]或[GO]来启动游戏。";
            this.breakLine();
        }
    }
    fun exit(number: Long) {
        if (this.state == STATE_WAIT) {
            val index = this.getPlayer(number);
            if (index != -1) {
                this.players.removeAt(index)
                this.msg += "退出成功，剩余玩家${this.players.size}人";
                if (this.players.size > 0) {
                    this.msg += "，分别是：\n"
                    this.msg += this.playersInfo()
                }
                else {
                    this.msg += "。"
                }
            }
        } else {
            this.msg += "游戏已经开始不能退出！\n"
            this.msg += "但你可以使用[弃牌]来放弃游戏！"
        }
    }

    fun joinWatching(playNum: Long) {
        val playIndex = this.getPlayer(playNum)
        val watchIndex = this.getWatcher(playNum)

        //非弃牌玩家不能观战
        if (playIndex != -1 && !players[playIndex].isSurrender) {
            this.msg += this.at(playNum)
            this.breakLine()
            this.msg += "你已经加入游戏，请不要通过观战作弊！"
            this.breakLine()
            return
        }
        if (watchIndex != -1) {
            this.msg += this.at(playNum);
            this.breakLine();
            this.msg += "你已经加入观战模式，不能重复加入";
            this.breakLine();
            return;
        }
        if (this.players.size < 3) {
            this.msg += this.at(playNum)
            this.breakLine()
            this.msg += "游戏人数不足，当前无法加入观战模式。"
            this.breakLine()
            return;
        }

        val watcher = Watcher(playNum)
        this.watchers += watcher
        sendWatchingMsg_Join(playNum)
    }

    fun exitWatching(playNum: Long) {
        val index = this.getWatcher(playNum);
        if (index != -1) {
            watchers.removeAt(index)
            this.msg += this.at(playNum)
            this.breakLine()
            this.msg += "退出观战模式成功。"
        }
    }

    fun sendWatchingMsg_Join(joinNum: Long) {
        val index = getWatcher(joinNum)
        val watcher = watchers[index]

        watcher.msg += "加入观战模式成功。"
        watcher.breakLine()

        watcher.msg += "---------------"
        //watcher->breakLine();
        //watcher->msg += "本局积分：" << this->multiple += " x " << this->basic += " = " << this->basic*this->multiple;
        watcher.breakLine()
        watcher.msg += "当前手牌信息："
        watcher.breakLine()
        watcher.msg += listPlayers(1)
        //不需要换行 watcher->breakLine();
    }

    fun sendWatchingMsg_Start() {
        for(watcher in watchers) {
            // TODO: 生成一次msg后所有watcher共享
            if (! isSecondCallForBoss) {
                watcher.msg += "斗地主游戏开始"
            } else {
                watcher.msg += "重新发牌"
            }
            watcher.breakLine();
            //这里不需要this->setNextPlayerIndex();
            watcher.msg += "---------------"
            //watcher->breakLine();
            //watcher->msg += "第" << this->turn + 1 += "回合：";
            //watcher->breakLine();
            //watcher->msg += "本局积分：" << this->multiple += " x " << this->basic += " = " << this->basic*this->multiple;
            watcher.breakLine()
            watcher.msg += "初始手牌："
            watcher.breakLine()
            watcher.msg += listPlayers(1)
        }
    }

    fun sendWatchingMsg_Play() {
        for (watcher in watchers) {

            //watcher->msg += "上回合";
            watcher.at(this.players[currentPlayIndex].number)
            watcher.msg += "打出" + this.lastCardType
            watcher.msg += this.lastCard.joinToString { "[$it]" }
            watcher.breakLine()

            //这里不需要this->setNextPlayerIndex();
            watcher.msg += "---------------";
            //watcher->breakLine();
            //watcher->msg += "第" << this->turn + 1 += "回合：";
            //watcher->breakLine();
            //watcher->msg += "本局积分：" << this->multiple += " x " << this->basic += " = " << this->basic*this->multiple;
            watcher.breakLine();
            watcher.msg += "当前剩余手牌："
            watcher.breakLine();
            watcher.msg += this.listPlayers(1)
            watcher.breakLine();
            watcher.msg += "现在轮到"
            watcher.at(this.players[this.currentPlayIndex].number)
            watcher.msg += "出牌。"
            watcher.breakLine();
        }
    }

    fun sendWatchingMsg_Pass(playNum: Long) {
        for(watcher in watchers) {
            watcher.at(playNum);
            watcher.msg += "过牌，";

            watcher.msg += "现在轮到";
            watcher.at(this.players[this.currentPlayIndex].number);
            watcher.msg += "出牌。\n"
        }
    }

    fun sendWatchingMsg_Surrender(playNum: Long) {
        for(watcher in watchers) {
            watcher.at(playNum);
            watcher.msg += "弃牌，"

            watcher.msg += "现在轮到"
            watcher.at(this.players[this.currentPlayIndex].number)
            watcher.msg += "出牌。\n"
        }
    }

    fun sendWatchingMsg_Over() {
        for (watcher in watchers) {

            watcher.msg += "斗地主游戏结束，";
            watcher.msg += if (this.whoIsWinner == 1) "地主赢了" else "农民赢了"
            watcher.breakLine()
            watcher.msg += "退出观战模式。"
            watcher.breakLine()
        }
    }


    fun startGame() {
        if (this.players.size == 3 && this.state == STATE_WAIT) {
            this.state = STATE_START;

            //启动挂机检测程序
            // this.counter = new thread(&Desk::checkAFK, this);

            var maxScore = players.maxOf { Admin.readScore(it.number) }
            var minScore = players.minOf { Admin.readScore(it.number) }

            //暂定：最大最低分每差50分标准分加3。
            for (player in players) {
                Admin.addScore(player.number, CONFIG_PLAY_BONUS);
            }

            this.basic = min(CONFIG_TOP_SCORE, (CONFIG_BOTTOM_SCORE * (maxScore - minScore)) / 50L)

            this.msg += "游戏开始，桌号：${this.number}。\n"
            this.msg += "游戏挂机检测时间：" +
                "抢地主${CONFIG_TIME_BOSS}秒，" +
                "加倍${CONFIG_TIME_MULTIPLE }秒，" +
                "出牌${CONFIG_TIME_GAME}秒，\n（暂未实装）\n"

            //this->msg += "准备环节可以进行[明牌]操作，明牌会使积分倍数 + 2，请谨慎操作！";
            //this->breakLine();
            this.msg += "---------------"
            this.breakLine();

            this.msg += this.listPlayers(1)

            this.shuffle()
            this.deal()

            this.createBoss();
        } else {
            if (this.state >= STATE_BOSSING) {
                //启动游戏往往会多次发送出发提示，此处不再做无效提示
                return;
            } else {
                this.msg += "没有足够的玩家。";
                this.breakLine();
                this.msg += playersInfo()
            }
        }
    }

    fun listCardsOnDesk(player: Player): String {
        return player.card.map { "[$it]" }.joinToString("")
    }

    /*
    fun AFKHandle(playNum: Long) {
        // time_t rawtime;
        // int64_t timeNow = time(&rawtime);

        if (this->state < STATE_BOSSING) {
            return;
        }

        if (timeNow - this->lastTime > 50) {
            this->at(playNum);
            this->breakLine();
            this->msg += "举报成功。";
            this->breakLine();

            Admin::addScore(this->players[this->currentPlayIndex]->number, -CONFIG_SURRENDER_PENALTY);
            Admin::addScore(playNum, CONFIG_SURRENDER_PENALTY);

            this->at(this->players[this->currentPlayIndex]->number);
            this->msg += "[" += "挂机-" << CONFIG_SURRENDER_PENALTY += "分]";
            this->breakLine();
            this->at(playNum);
            this->msg += "[" += "举报+" << CONFIG_SURRENDER_PENALTY << L"分]";
            this->breakLine();


            this->setNextPlayerIndex();
            this->lastTime = timeNow;

            this->msg << L"现在轮到";
            this->at(this->players[this->currentPlayIndex]->number);
            this->msg << L"出牌。";
            this->breakLine();
        }
        else {
            this->at(playNum);
            this->breakLine();
            this->msg << L"举报失败，";
            this->at(this->players[this->currentPlayIndex]->number);
            this->msg << L"的剩余出牌时间为" << this->lastTime + CONFIG_TIME_GAME - timeNow << L"秒。";
        }
    }
    */

    /*
void Desk::checkAFK() {
	time_t rawtime;
	int64_t timeNow = time(&rawtime);

	while (this->state < STATE_BOSSING) {
		std::this_thread::sleep_for(std::chrono::milliseconds(500));
		;
	}

	this->warningSent = false;

	while (this->state == STATE_BOSSING) {
		std::this_thread::sleep_for(std::chrono::milliseconds(500));
		timeNow = time(&rawtime);

		//this->msg << L"测试信息：";
		//this->at(this->players[this->currentPlayIndex]->number);
		//this->msg << L"的剩余出牌时间为" << this->lastTime + CONFIG_TIME_GAME - timeNow << L"秒。";

		if (timeNow - this->lastTime > CONFIG_TIME_BOSS) {
			this->warningSent = false;
			Admin::addScore(this->players[this->currentPlayIndex]->number, -CONFIG_SURRENDER_PENALTY);

			this->at(this->players[this->currentPlayIndex]->number);
			this->msg << L"[" << L"挂机-" << CONFIG_SURRENDER_PENALTY << L"分]";
			this->breakLine();
			this->msg << L"---------------";
			this->breakLine();
			//this->setNextPlayerIndex();
			//this->lastTime = timeNow;

			this->dontBoss(this->players[this->currentPlayIndex]->number);

			this->sendMsg(this->subType);
			this->msg.str(L"");
		}
		else if (!this->warningSent && timeNow - this->lastTime > CONFIG_TIME_BOSS - CONFIG_TIME_WARNING) {
			this->warningSent = true;
			this->at(this->players[this->currentPlayIndex]->number);
			this->msg << L"抢地主时间剩余" << CONFIG_TIME_WARNING << L"秒。";
			this->breakLine();

			this->sendMsg(this->subType);
			this->msg.str(L"");
		}
	}

	//防止提示后抢地主出现bug
	this->warningSent = false;

	while (this->state == STATE_MULTIPLING) {
		std::this_thread::sleep_for(std::chrono::milliseconds(500));
		timeNow = time(&rawtime);

		//this->msg << L"测试信息：";
		//this->at(this->players[this->currentPlayIndex]->number);
		//this->msg << L"的剩余出牌时间为" << this->lastTime + CONFIG_TIME_GAME - timeNow << L"秒。";

		if (timeNow - this->lastTime > CONFIG_TIME_MULTIPLE) {
			this->warningSent = false;
			Admin::addScore(this->players[this->currentPlayIndex]->number, -CONFIG_SURRENDER_PENALTY);

			this->at(this->players[this->currentPlayIndex]->number);
			this->msg << L"[" << L"挂机-" << CONFIG_SURRENDER_PENALTY << L"分]";
			this->breakLine();
			this->msg << L"---------------";
			this->breakLine();
			//this->setNextPlayerIndex();
			//this->lastTime = timeNow;

			this->dontMultiple(this->players[this->currentPlayIndex]->number);

			this->sendMsg(this->subType);
			this->msg.str(L"");
		}
		else if (!this->warningSent && timeNow - this->lastTime > CONFIG_TIME_MULTIPLE - CONFIG_TIME_WARNING) {
			this->warningSent = true;
			this->at(this->players[this->currentPlayIndex]->number);
			this->msg << L"加倍选择时间剩余" << CONFIG_TIME_WARNING << L"秒。";
			this->breakLine();

			this->sendMsg(this->subType);
			this->msg.str(L"");
		}
	}

	//防止提示后加倍出现bug
	this->warningSent = false;

	while (this->state == STATE_READYTOGO || this->state == STATE_GAMING) {
		std::this_thread::sleep_for(std::chrono::milliseconds(500));
		timeNow = time(&rawtime);

		//this->msg << L"测试信息：";
		//this->at(this->players[this->currentPlayIndex]->number);
		//this->msg << L"的剩余出牌时间为" << this->lastTime + CONFIG_TIME_GAME - timeNow << L"秒。";

		if (timeNow - this->lastTime > CONFIG_TIME_GAME) {
			this->warningSent = false;
			Admin::addScore(this->players[this->currentPlayIndex]->number, -CONFIG_SURRENDER_PENALTY);

			this->at(this->players[this->currentPlayIndex]->number);
			this->msg << L"[" << L"挂机-" << CONFIG_SURRENDER_PENALTY << L"分]";
			this->breakLine();
			this->msg << L"---------------";
			this->breakLine();
			this->setNextPlayerIndex();
			this->lastTime = timeNow;

			this->msg << L"现在轮到";
			this->at(this->players[this->currentPlayIndex]->number);
			this->msg << L"出牌。";
			this->breakLine();

			this->sendMsg(this->subType);
			this->msg.str(L"");
		}
		else if (!this->warningSent && timeNow - this->lastTime > CONFIG_TIME_GAME - CONFIG_TIME_WARNING) {
			this->warningSent = true;
			this->at(this->players[this->currentPlayIndex]->number);
			this->msg << L"出牌时间剩余" << CONFIG_TIME_WARNING << L"秒。";
			this->breakLine();

			this->sendMsg(this->subType);
			this->msg.str(L"");
		}
	}
	}
     */
    fun playersInfo(): String {
        var ret: String = ""
        for (i in 0..players.size - 1) {
            ret += "${i + 1}号玩家："
            ret += this.getPlayerInfo(players[i].number)
            ret += "\n"
        }
        return ret
    }

    fun commandList() {
        // TODO: List.joinToString
        this.msg +=
            "斗地主命令列表（*号表示支持后带符号）：" + "\n" +
                "0*. 斗地主版本：查看游戏版本号、GitHub链接与原作者信息\n" +
                "1*. 上桌|打牌：加入游戏\n" +
                "2*. 出|打：出牌 比如 出23456！\n" +
                "3*. 过(牌)|不要|pass：过牌\n" +
                "4*. 抢(地主)|不抢：是否抢地主\n" +
                "5*. 加(倍)|不加(倍)：是否加倍\n" +
                "6*. 开始|启动|GO：是否开始游戏\n" +
                "7*. 下桌|不玩了：退出游戏，只能在准备环节使用\n" +
                "8. 玩家列表：当前在游戏中得玩家信息\n" +
                "9*. 明牌：显示自己的牌给所有玩家，明牌会导致积分翻倍，只能在发完牌后以及出牌之前使用。\n" +
                "10*. 弃牌：放弃本局游戏，当地主或者两名农民弃牌游戏结束。农民玩家弃牌赢了不得分（有弃牌惩罚），输了双倍扣分" + "\n" +
                // "11. 获取积分（已废弃）：获取积分，每天可获取200积分。" + "\n"
                "11. 我的信息：查看我的战绩与积分信息（群聊私聊皆可）" + "\n" +
                "12. 加入观战|观战：暗中观察" + "\n" +
                "13. 退出观战：光明正大的看打牌" + "\n" +
                //<< L"14*. 举报|挂机|AFK：超过" << CONFIG_TIME_GAME
                //	<< L"秒不出牌，可以举报，举报成功的奖励" << CONFIG_SURRENDER_PENALTY
                //	<< L"分，被举报的扣除" << CONFIG_SURRENDER_PENALTY << L"分。" << "\r\n"
                "A1. " + "我是管理：绑定游戏管理员为当前发送消息的qq，管理员可使用管理命令。管理设置后不能更改" + "\n" +
                "A2. " + "重置斗地主：删除所有配置。重置后可重新设定管理员" + "\n" +
                "A3. " + "结束游戏[群号]：结束指定群号的游戏，比如：结束游戏123456" + "\n" +
                "A4. " + "设置积分[qq号]=[积分]：给指定qq分配积分，如：设置积分123456=-998" + "\n" +
                "A5. " + "改变数据类型：切换“正式数据”与“测试数据”类型" + "\n" +
                "A6. " + "备份数据：你懂的，防止服务器爆炸"
    }
}
