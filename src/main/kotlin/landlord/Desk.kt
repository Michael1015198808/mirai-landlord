package michael.landlord.main

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import michael.landlord.Config
import michael.landlord.PluginMain
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.messageChainOf
import java.util.*
import kotlin.random.Random

data class NotifyTask(
    val desk: Desk,
    val index: Int
): TimerTask() {
    override fun run() {
        runBlocking {
            desk.mutex.withLock {
                if(desk.currentPlayIndex == index) {
                    desk.group.sendMessage(
                        messageChainOf(
                            At(desk.players[index].number),
                            PlainText(" 轮到你出牌啦！")
                        )
                    )
                }
            }
        }
    }
}

data class Desk(
    val number: Long, // 桌号，即群号
    val group: Group,
) {
    var turn: Int = 0
    var multiple: Long = 1 // 分数倍率
    val basic: Long = CONFIG_INIT_SCORE
    var lastTime: Long = 0
    var cards: MutableList<String> = cardDest.toMutableList()
    @Volatile
    var players: MutableList<Player> = mutableListOf()
    var watchers: Watchers = Watchers()
    // TODO: 倒计时器
    // thread *counter;	//倒计时器

    var whoIsWinner: Int = 0
    var state: Int = STATE_WAIT
    var lastPlayIndex: Int = -1//当前谁出的牌
    var currentPlayIndex: Int = -1//该谁出牌
    // TODO: 改名为landlord
    var bossIndex: Int = -1//谁是地主
    var multipliedCount: Int = 0
    var isSecondCallForBoss: Boolean = false//第二次叫地主
    // 是否是反抢的地主
    var isForceBoss: Boolean = false
    var warningSent: Boolean = false//倒计时警告消息已发送

    var subType: Boolean = false//存储消息类型

    var bossCount: Int = 0//记录出牌次数，检测春天
    var farmCount: Int = 0

    var lastCard: List<String> = listOf() //上位玩家的牌
    var lastCardType: String = "" //上位玩家的牌类
    var lastWeight: Int = 0 //上位玩家的牌的权重

    var msg: String = ""
    val mutex = Mutex()
    val timer = Timer()

    fun at(playNum: Long): String {
        // TODO: Allow @people
        return "[mirai:at:$playNum]";
    }

    fun breakLine() {
        msg += "\n"
    }

    fun getPlayer(number: Long): Player? {
        return players.firstOrNull { it.number == number }
    }

    fun getPlayerId(number: Long): Int {
        return players.indexOfFirst { it.number == number }
    }

    fun listPlayers(type: Int): String {
        val hasWin: Boolean  = type.and(2) > 0
        val listHandCards: Boolean  = type.and(4) > 0

        // TODO: Test mutablelist's speed and String's speend
        val ret: MutableList<String> = mutableListOf()
        val score = if (isForceBoss && whoIsWinner == 2) {
            ret += "地主反抢后失败，分数倍率×2\n"
            basic * 2
        } else {
            basic
        } * multiple

        if (players.size < 3) {
            ret += "游戏尚未开始，玩家列表：\n"
        } else {
            ret += "本局积分：${score}\n"
            ret += "出牌次数：$turn\n"
        }
        if(hasWin) {
            val landlord_flag: Boolean = (whoIsWinner == 1) //如果是地主赢了
            val scores = players.map { PluginMain.readScore(it.number) }
            val average_score =  scores.sum() / 3
            val factors = scores.map { ((average_score - it) / Config.factor).coerceIn(-20, 20) }.toMutableList()
            factors[bossIndex] = 0
            factors[bossIndex] = -factors.sum()

            if(landlord_flag) ++PluginMain.globalStatisticsData.landlord_wins
            else ++PluginMain.globalStatisticsData.landlord_loses

            fun calFactor(isBoss: Boolean, factor: Long): Long {
                return factor + (if (isBoss) 200 else 100)
            }
            for((i, player) in players.withIndex()) {
                ret += "${i + 1}号玩家"
                ret += at(player.number)
                ret += "[${if (i == bossIndex) "地主" else "农民"}]："
                var add_score: Long
                if (player.isSurrender) {
                    val factor = calFactor(i == bossIndex, -factors[i])
                    add_score = - CONFIG_SURRENDER_PENALTY * multiple * factor / 100
                    ret += "投降\n${add_score}分（${100 + factors[i]}%）"
                    PluginMain.addLose(player.number, i == bossIndex)
                } else if ((i == bossIndex).xor(landlord_flag)) {
                    val factor = calFactor(i == bossIndex, -factors[i])
                    add_score = -score * factor / 100
                    ret += "失败\n${add_score}分（${factor}%）"
                    PluginMain.addLose(player.number, i == bossIndex)
                } else {
                    val factor = calFactor(i == bossIndex, factors[i])
                    add_score = score * factor / 100
                    ret += "胜利\n+${add_score}分（${factor}%）"
                    PluginMain.addWin(player.number, i == bossIndex)
                }
                PluginMain.addScore(player.number, add_score)
                ret += "，当前积分${PluginMain.readScore(player.number)}"
                //如果还有牌，就公开手牌
                if (player.card.size > 0) {
                    ret += "\n剩余手牌："
                    ret += player.handCards()
                }
                ret += "\n"
            }
        } else {
            for((i, player) in players.withIndex()) {
                ret += "${i+1}号玩家"
                ret += at(player.number)
                if (state >= STATE_READYTOGO) {
                    ret += "[${if(i == bossIndex) "地主" else "农民"}]"
                }
                ret += "：${player.card.size}张手牌"
                if (listHandCards) {
                    ret += player.handCards()
                }
                ret += "\n"
            }
        }
        return ret.joinToString("")
    }

    fun isCanWin(cardCount: Int, weight: Int, type: String): Boolean {
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
        return type == lastCardType && cardCount == lastCard.size&& weight > lastWeight
    }

    fun getMycardType(list: List<String>): Pair<String, Int> {
        val cardCount = list.size

        if (cardCount == 2 && Util.findFlag(list[0]) + Util.findFlag(list[1]) == 27) {//王炸
            return Pair("王炸", 13)
        }

        val cards: MutableList<String> = mutableListOf()
        // 去重列表，由小到大排序
        val counts: MutableList<Int> = mutableListOf()

        for (card in list.sortedBy { Util.findFlag(it) }) {
            val index: Int = cards.indexOf(card)
            if (index == -1) {
                cards += card
                counts += 1
            } else {
                ++counts[index]
            }
        }

        val max_count = counts.maxOf { it }
        val min_count = counts.minOf { it }
        var cardGroupCount = cards.size

        if (cardCount == 1) {
            return Pair("单牌", Util.findFlag(cards[0]))
        }
        if (cardCount == 2 && max_count == 2) {
            return Pair("对子", Util.findFlag(cards[0]))
        }
        if (cardCount == 3 && max_count == 3) {
            return Pair("三张", Util.findFlag(cards[0]))
        }
        if (cardCount == 4 && max_count == 4) {
            return Pair("炸弹", Util.findFlag(cards[0]))
        }
        if (cardCount == 4 && max_count == 3) {
            return Pair("3带1", Util.findFlag(cards[counts.indexOf(3)]))
        }
        if (cardCount == 5 && max_count == 3 && min_count == 2) {
            return Pair("3带2", Util.findFlag(cards[counts.indexOf(3)]))
        }
        if (cardCount == 6 && max_count == 4) {
            return Pair("4带2", Util.findFlag(cards[counts.indexOf(4)]))
        }
        if (cardCount == 8 && max_count == 4 && min_count == 2) {
            return Pair("4带2对", Util.findFlag(cards[counts.indexOf(4)]))
        }
        if (cardGroupCount * max_count > 4 && max_count == min_count && max_count < 4 // 不允许 33334444
            && Util.findFlag(cards[cardGroupCount - 1]) - Util.findFlag(cards[0]) + 1 == cardGroupCount
            && Util.findFlag(cards[cardGroupCount - 1]) < 12
        ) { //顺子
            val model = listOf("顺子", "连对", "飞机")[max_count - 1]
            return Pair(model, Util.findFlag(cards[0]))
        }

        if (list.size % 4 == 0) {
            // 判断 AAAB 型飞机
            val len = list.size / 4
            val lastTrip = counts.lastIndexOf(3)
            val firstTrip = counts.indexOf(3)
            if(counts.subList(lastTrip - len + 1, lastTrip + 1).all { it >= 3 } &&
                Util.findFlag(cards[lastTrip]) - Util.findFlag(cards[lastTrip - len + 1]) + 1 == len) {
                // 飞机的主体必然包含三张的牌中最大的或最小的 // 都是三张且连续
                return Pair("飞机带${len}翅膀", Util.findFlag(cards[lastTrip - len + 1]))
            }
            if(counts.subList(firstTrip, firstTrip + len).all { it >= 3 } &&
                Util.findFlag(cards[firstTrip + len - 1]) - Util.findFlag(cards[firstTrip]) + 1 == len) {
                return Pair("飞机带${len}翅膀", Util.findFlag(cards[firstTrip]))
            }
        }
        if (list.size % 5 == 0 && min_count > 1) {
            // 判断 AAABB 型飞机
            val len = list.size / 5
            val lastTrip = counts.lastIndexOf(3)
            val firstTrip = counts.indexOf(3)
            if(counts.subList(lastTrip - len + 1, lastTrip + 1).all { it >= 3 } &&
                Util.findFlag(cards[lastTrip]) - Util.findFlag(cards[lastTrip - len + 1]) + 1 == len) {
                return Pair("飞机带${len}翅膀", Util.findFlag(cards[lastTrip - len + 1]))
            }
            if(counts.subList(firstTrip, firstTrip + len).all { it >= 3 } &&
                Util.findFlag(cards[firstTrip + len - 1]) - Util.findFlag(cards[firstTrip]) + 1 == len) {
                return Pair("飞机带${len}翅膀", Util.findFlag(cards[firstTrip]))
            }
        }
        return Pair("", 0)
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
        watchers.sendMsg()
    }

    fun createBoss() {
        state = STATE_BOSSING

        //记录时间
        // time_t rawtime;
        // lastTime = time(&rawtime);
        //防止bug
        warningSent = false

        msg += "下面进入抢地主环节，倒计时开始。\n"
        msg += Util.crossline()

        val index = Random.nextInt(0, 3)

        bossIndex = index
        currentPlayIndex = index
        msg += at(players[index].number)
        breakLine()
        msg += "你是否要抢地主？\n请用[抢]或[不(抢)]来回答。\n"
        //战况播报
        sendWatchingMsg_Start()
    }

    fun getLandlord(playerNum: Long) {
        val index = getPlayerId(playerNum);
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
        val index = getPlayerId(playerNum)
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
                msg += "第1次抢地主失败，重新发牌。\n"
                msg += Util.crossline()
                isSecondCallForBoss = true
                this.cards.shuffle()
                deal();
                createBoss();
                return;
            }
            else {
                msg += at(players[index].number);
                msg += "不抢地主。";
                breakLine();
                msg += Util.crossline()
                msg += at(players[currentPlayIndex].number);
                breakLine();
                msg += "你是否要抢地主？";
                breakLine();
            }
        }
    }


    fun sendBossCard() {
        val playerBoss: Player = players[bossIndex]

        msg += at(playerBoss.number)
        msg += "是地主，底牌是：";
        msg += cards.subList(51, 54).joinToString("") { "[$it]" }
        breakLine();
        msg += Util.crossline()
        breakLine();

        playerBoss.card.addAll(cards.subList(51, 54))

        playerBoss.card.sortBy { Util.findFlag(it) }

        //playerBoss->msg << L"你是地主，收到底牌：";
        //playerBoss->breakLine();
        playerBoss.msg += playerBoss.handCards()

        //这里的状态声明移动到了加倍(dontMultiple)的最后一个人语句哪里。
    }

    fun multipleChoice() {
        //记录时间
        // time_t rawtime;
        // lastTime = time(&rawtime);
        //防止bug
        warningSent = false;

        msg += """
            抢地主环节结束，下面进入加倍环节。
            ${players.joinToString(" ") {this.at(it.number)}}
            是否要加倍？
            请用[加]或[不(加)]来回答。
            """.trimIndent()
        if (Config.反抢 && !isForceBoss) {
            msg += """
            如果要反抢请输入[反抢]。
            （注：反抢后获胜，基本分不变。反抢后失败，基本分按2倍计算）
            """.trimIndent()
        }
    }

    fun forceLandlord(playerId: Long) {
        when(val index = getPlayerId(playerId)) {
            -1 -> {
                if(Config.verbose) {
                    this.msg += "你不是玩家！"
                }
            }
            bossIndex -> {
                msg += at(playerId) + "你已经是地主，不能反抢自己！"
            }
            else -> {
                //记录时间
                // time_t rawtime;
                // lastTime = time(&rawtime);
                //防止提示后抢地主出现bug
                warningSent = false

                cards.subList(51, 54).map {
                    players[bossIndex].card.remove(it)
                }
                players[bossIndex].msg += "被反抢，手牌变为：\n" + players[bossIndex].handCards()
                bossIndex = index
                currentPlayIndex = index
                lastPlayIndex = index
                sendBossCard()
                isForceBoss = true

                //进入加倍环节
                players.map { it.hasMultiplied = false }
                multipliedCount = 0
                multiple = 1
                multipleChoice()
            }
        }
    }

    fun setMultiple(playerNum: Long, confirmMultiple: Boolean) {
        val player = getPlayer(playerNum)
        if (player != null) {
            if (! player.hasMultiplied) {
                player.hasMultiplied = true
                msg += at(player.number);
                if(confirmMultiple) {
                    multiple += 1
                    msg += "要加倍。\n"
                    msg += "本局积分：${basic * multiple}\n"
                } else {
                    msg += "不加倍。\n"
                }
                multipliedCount += 1

                //记录时间
                // time_t rawtime;
                // lastTime = time(&rawtime);
                //防止提示后加倍出现bug
                warningSent = false;


                if (multipliedCount == 3) {
                    state = STATE_READYTOGO;
                    msg += """
                        加倍环节结束，${multiple - 1}人加倍。斗地主正式开始。
                        ${Util.crossline()}
                        """.trimIndent() +
                        listPlayers(1) +
                        "请地主${at(players[bossIndex].number)}先出牌"
                    //战况播报
                    sendWatchingMsg_Start()
                }
            }
        }
    }

    fun play(playNum: Long, raw_msg: String) {
        val playIndex: Int = this.getPlayerId(playNum)

        if (playIndex == -1) {
            if (Config.verbose) {
                this.msg += "你不是玩家！"
            }
            return
        }
        if (playIndex != this.currentPlayIndex) {
            if (Config.verbose) {
                this.msg += "还没轮到你出牌"
            }
            return
        }
        if((!(this.state == STATE_GAMING && this.turn > 0)
                && !(this.state == STATE_READYTOGO && this.turn == 0))) {
            this.msg += "游戏尚未开始！"
            return
        }
        play_real(playIndex, Util.stringToCards(raw_msg))
    }

    fun play_real(playIndex: Int, list: MutableList<String>) {
        if(list.size == 0) {
            this.msg += "请跟上你要打出的牌，如\"出33344\"或\"打34567\"或\"445566\""
            return
        }

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

        val (type, weight) = getMycardType(list)

        val isCanWin: Boolean = this.isCanWin(cardCount, weight, type)

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
            this.lastWeight = weight
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
                this.msg += Util.crossline()
            } else if (type == "炸弹") {
                this.multiple += 1;

                this.msg += "打出炸弹，积分倍数+1\n"
                this.msg += "本局积分：${this.basic*this.multiple}\n"
                this.msg += Util.crossline()
            }

            if (mycardTmp.size == 0) {//赢了。
                this.whoIsWinner = if (this.bossIndex == this.currentPlayIndex) 1 else 2

                this.sendWatchingMsg_Over()

                this.msg += "斗地主游戏结束，"
                this.msg += if(this.whoIsWinner == 1) "地主赢了" else "农民赢了"
                this.breakLine()

                if (this.farmCount == 0 && this.whoIsWinner == 1) {
                    this.multiple *= 2;
                    this.msg += Util.crossline()
                    this.msg += "本局出现春天，积分倍数x2\n"
                    this.msg += "本局积分：${this.basic*this.multiple}\n"
                } else if (this.bossCount == 1 && this.whoIsWinner == 2) {
                    this.multiple *= 2;
                    this.msg += Util.crossline()
                    this.msg += "本局出现反春天，积分倍数x2\n"
                    this.msg += "本局积分：${this.basic*this.multiple}\n"
                }
                this.msg += Util.crossline()
                this.msg += "分数结算：\n"
                this.msg += this.listPlayers(3);

                timer.purge()
                Casino.gameOver(this.number);
                return
            }

            player.msg += player.handCards()

            if (player.isOpenCard) {
                this.msg += this.at(player.number)
                this.msg += "明牌："
                this.msg += player.handCards()
                this.breakLine()
                this.msg += Util.crossline()
            }

            if (player.card.size < 3) {
                this.msg += "红色警报！红色警报！\n"
                this.msg += this.at(player.number)
                this.msg += "仅剩下${player.card.size}张牌！\n"
                this.msg += Util.crossline()
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

            //this.msg << L"第" << this.turn + 1 << L"回合：";
            //this.breakLine();
            if (Config.verbose) {
                this.msg += Util.crossline()
                this.msg += this.listPlayers(1)
            } else {
                this.msg += "${playIndex+1}号玩家${at(player.number)}" +
                    "[${if(playIndex == bossIndex) "地主" else "农民"}]" +
                    "：${player.card.size}张手牌"
                if (player.isOpenCard) {
                    this.msg += player.handCards()
                }
                this.msg += "\n"
            }
            this.msg += "现在轮到"
            this.msg += this.at(this.players[this.currentPlayIndex].number)
            this.msg += "出牌。\n"
            this.timer.purge()
        }
        else {
            this.msg += this.at(this.players[this.currentPlayIndex].number)
            this.breakLine()
            this.msg += "傻逼网友，打的什么几把玩意！学会出牌再打！\n"
        }
    }

    fun discard(playNum: Long) {
        if (this.currentPlayIndex != this.getPlayerId(playNum) || this.state != STATE_GAMING) {
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

        this.setNextPlayerIndex();
        if (Config.verbose) {
            this.msg += this.at(playNum)
            this.msg += "过牌，"
            this.msg += "现在轮到";
            this.msg += this.at(this.players[this.currentPlayIndex].number)
            this.msg += "出牌。\n"
        }
        this.timer.purge()
        this.timer.schedule(NotifyTask(this, currentPlayIndex), Config.timeout)

        //观战播报
        this.sendWatchingMsg_Pass(playNum);
    }

    fun surrender(playNum: Long) {
        val index = this.getPlayerId(playNum)
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

            this.msg += Util.crossline()
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
        val player = this.getPlayer(playNum);

        if (player == null) {
            if (Config.verbose) {
                this.msg += "你不是玩家，不能明牌！"
            }
            suspend { this.sendMsg(true) }
            return;
        }
        if (this.state > STATE_READYTOGO || this.state < STATE_START) {
            this.msg += this.at(playNum)
            this.breakLine()
            this.msg += "明牌只能在准备阶段使用！"
            return
        }

        if (!player.isOpenCard) {
            player.isOpenCard = true;
            this.multiple += 2;
        }

        this.msg += this.at(playNum);
        this.msg += "明牌，积分倍数+2。\n"

        this.msg += player.handCards()
        this.breakLine()

        this.msg += Util.crossline()
        this.msg += "本局积分：${this.basic*this.multiple}"
    }

    //私人查询信息
    fun getPlayerInfo(playerId: Long): String {
        val player = PluginMain.getPlayerInfo(playerId)
        return """
            ${this.at(playerId)}
            ${player.wins}胜，${player.loses}负，积分${player.score}""".trimIndent()
    }

    fun detailedInfo(playerId: Long) {
        val player = PluginMain.getPlayerInfo(playerId)
        fun winning_rate(win: Long, lose: Long): String {
            if(win + lose == 0L) {
                return "0%"
            }
            return "${(win * 100) / (win + lose)}%"
        }
        this.msg += """
            ${this.at(playerId)}
            积分${player.score}
            ${player.wins}胜，${player.loses}负，胜率${winning_rate(player.wins, player.loses)}
            农民时${player.farmer_wins}胜，${player.farmer_loses}负，胜率${winning_rate(player.farmer_wins, player.farmer_loses)}
            地主时${player.landlord_wins}胜，${player.landlord_loses}负，胜率${winning_rate(player.landlord_wins, player.landlord_loses)}
            """.trimIndent()
    }

    fun setNextPlayerIndex() {
        this.currentPlayIndex = (this.currentPlayIndex + 1) % 3

        if (this.currentPlayIndex == this.lastPlayIndex) {
            this.lastCard = listOf()
            this.lastWeight = 0
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
        if (this.getPlayerId(playNum) != -1) {
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

        if (this.players.size == 3) {
            this.breakLine();
            this.msg += "人数已满，";
            this.msg += "请输入[启动]或[GO]来启动游戏。";
            this.breakLine();
        }
    }
    fun exit(number: Long) {
        if (this.state == STATE_WAIT) {
            val index = this.getPlayerId(number);
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

    fun joinWatching(playNum: Long): Boolean {
        val player = this.getPlayer(playNum)

        //非弃牌玩家不能观战
        if (player != null && !player.isSurrender) {
            this.msg += this.at(playNum)
            this.breakLine()
            this.msg += "你已经加入游戏，请不要通过观战作弊！"
            this.breakLine()
            return false
        }
        if (watchers.contains(playNum)) {
            this.msg += this.at(playNum);
            this.breakLine();
            this.msg += "你已经加入观战模式，不能重复加入";
            this.breakLine();
            return false
        }
        if (this.players.size < 3) {
            this.msg += this.at(playNum)
            this.breakLine()
            this.msg += "游戏人数不足，当前无法加入观战模式。"
            this.breakLine()
            return false
        }

        return this.watchers.add(playNum)
    }

    fun exitWatching(playNum: Long) {
        if (watchers.contains(playNum)) {
            watchers.remove(playNum)
            this.msg += this.at(playNum)
            this.breakLine()
            this.msg += "退出观战模式成功。"
        }
    }

    fun sendWatchingMsg_Join(joinNum: Long) {
        val builder = StringBuilder()

        builder.append("加入观战模式成功。\n")
        builder.append(Util.crossline())
        //watcher->breakLine();
        //watcher->msg += "本局积分：" << this->multiple += " x " << this->basic += " = " << this->basic*this->multiple;
        builder.append("当前手牌信息：\n")
        builder.append(listPlayers(4))
        //不需要换行 watcher->breakLine();
        runBlocking { CQ_sendPrivateMsg(0, joinNum, builder.toString()) }
    }

    fun sendWatchingMsg_Start() {
        val builder = StringBuilder()
        if (! isSecondCallForBoss) {
            builder.append("斗地主游戏开始\n")
        } else {
            builder.append("重新发牌\n")
        }
        //这里不需要this->setNextPlayerIndex();
        builder.append(Util.crossline())
        //watcher->breakLine();
        //watcher->msg += "第" << this->turn + 1 += "回合：";
        //watcher->breakLine();
        //watcher->msg += "本局积分：" << this->multiple += " x " << this->basic += " = " << this->basic*this->multiple;
        builder.append("初始手牌：\n")
        builder.append(listPlayers(4))
        watchers.msg = builder.toString()
    }

    fun sendWatchingMsg_Play() {
        val builder = StringBuilder()

        //watcher->msg += "上回合";
        builder.append(this.at(this.players[currentPlayIndex].number))
        builder.append("打出" + this.lastCardType)
        builder.append(this.lastCard.joinToString("") { "[$it]" })
        //这里不需要this->setNextPlayerIndex();
        builder.append(Util.crossline())
        //watcher->breakLine();
        //watcher->msg += "第" << this->turn + 1 += "回合：";
        //watcher->breakLine();
        //watcher->msg += "本局积分：" << this->multiple += " x " << this->basic += " = " << this->basic*this->multiple;
        builder.append("当前剩余手牌：\n")
        builder.append(listPlayers(4))
        builder.append("现在轮到\n")
        builder.append(this.at(this.players[this.currentPlayIndex].number))
        builder.append("出牌。")

        watchers.msg = builder.toString()
    }

    fun sendWatchingMsg_Pass(playNum: Long) {
        val builder = StringBuilder()

        builder.append(this.at(playNum))
        builder.append("过牌，")
        builder.append("现在轮到")
        builder.append(this.at(this.players[this.currentPlayIndex].number))
        builder.append("出牌。\n")

        watchers.msg = builder.toString()
    }

    fun sendWatchingMsg_Surrender(playNum: Long) {
        val builder = StringBuilder()

        builder.append(this.at(playNum))
        builder.append("弃牌，")
        builder.append("现在轮到")
        builder.append(this.at(this.players[this.currentPlayIndex].number))
        builder.append("出牌。\n")

        watchers.msg = builder.toString()
    }

    fun sendWatchingMsg_Over() {
        val builder = StringBuilder()

        builder.append("斗地主游戏结束，")
        builder.append(if (this.whoIsWinner == 1) "地主赢了" else "农民赢了")
        builder.append("\n退出观战模式。\n")

        watchers.msg = builder.toString()
    }


    fun startGame() {
        if (this.players.size == 3 && this.state == STATE_WAIT) {
            this.state = STATE_START;

            //启动挂机检测程序
            // this.counter = new thread(&Desk::checkAFK, this);

            // this.basic = min(CONFIG_TOP_SCORE, (CONFIG_BOTTOM_SCORE * (maxScore - minScore)) / 50L)
            // this.basic = 0

            this.msg += "游戏开始，桌号：${this.number}。\n"
            this.msg += "游戏挂机检测时间：" +
                "抢地主${CONFIG_TIME_BOSS}秒，" +
                "加倍${CONFIG_TIME_MULTIPLE }秒，" +
                "出牌${CONFIG_TIME_GAME}秒，\n（暂未实装）\n"

            //this->msg += "准备环节可以进行[明牌]操作，明牌会使积分倍数 + 2，请谨慎操作！";
            //this->breakLine();

            this.cards.shuffle()
            this.deal()
            if (Config.verbose) {
                this.msg += Util.crossline()
                this.breakLine()
                this.msg += this.listPlayers(0)
            }

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
}
