package michael.landlord.main

object Admin {
    fun readAdmin(): Long {
        return GetPrivateProfileInt("admin", "admin", 0, CONFIG_PATH)
    }

    fun isAdmin(playNum: Long): Boolean {
        return if (playNum == readAdmin()) {
            true
        } else {
            suspend { Util.sendPrivateMsg(playNum, "你根本就不是管理员！") }
            false
        }
    }

    fun readString(): String {
        return GetPrivateProfileString("admin", "admin", "", CONFIG_PATH)
    }

    fun allotScoreTo(msg: String, playNum: Long): Boolean {
        // TODO: 分配正分
        /*
        var score: Int = 0
        var playerNum: Long

        wsmatch mr;
        wstring::const_iterator src_it = msg.begin(); // 获取起始位置
        wstring::const_iterator src_end = msg.end(); // 获取结束位置
        regex_search(src_it, src_end, mr, numberReg);
        wstringstream ss;
        ss << mr[0].str();
        ss >> playerNum;
        ss.str(L"");
        src_it = mr[0].second;
        regex_search(src_it, src_end, mr, numberReg);
        wstringstream scoress;
        scoress << mr[0].str();
        scoress >> score;
        scoress.str(L"");

        return Admin::isAdmin(playNum) && Admin::writeScore(playerNum, score+500000000);
         */
        return false
    }

    fun allotScoreTo2(msg: String, playNum: Long): Boolean {
        // TODO: 分配负分
        /*
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
        src_it = mr[0].second;
        regex_search(src_it, src_end, mr, numberReg);
        wstringstream scoress;
        scoress << mr[0].str();
        scoress >> score;
        score = -score;
        scoress.str(L"");

        return Admin::isAdmin(playNum) && Admin::writeScore(playerNum, score+500000000);
         */
        return false
    }

    fun gameOver(msg: String, playNum: Long): Boolean {
        return if (isAdmin(playNum)) {
            val destNum: Long = msg.substring(4).toLong()
            Casino.gameOver(destNum);
            true
        } else {
            false
        }
    }

    fun writeAdmin(playerNum: Long): Boolean {
        return WritePrivateProfileString("admin", "admin", playerNum.toString(), CONFIG_PATH);
    }

    fun readScore(playerNum: Long): Long {
        val model = "score";
        //增加负分功能，最低值负5亿分，第三个参数是未找到时返回的默认值。
        //负分直接输出有bug，所以输出需要使用desk中的readScore函数。
        return GetPrivateProfileInt(model, playerNum.toString(), 500000000, CONFIG_PATH)
    }

    fun getScore(playerNum: Long): Boolean {
        val lastGetScoreTime = GetPrivateProfileInt("time", playerNum.toString(), 0, CONFIG_PATH);
        // TODO: 时间
        /*
        time_t rawtime;
        int64_t now = time(&rawtime);

        return if (now / (24 * 60 * 60) > lastGetScoreTime / (24 * 60 * 60)) {
            addScore(playerNum, CONFIG_INIT_SCORE);
            WritePrivateProfileString("time", playerNum.toString(), new.toString(), CONFIG_PATH);
        } else {
            false
        }
         */
        return true
    }

    fun writeScore(playerNum: Long, score: Long): Boolean {
        //更新数据库版本
        writeVersion()
        return WritePrivateProfileString("score", playerNum.toString(), score.toString(), CONFIG_PATH);
    }

    fun addScore(playerNum: Long, score: Long): Boolean {
        var hasScore = readScore(playerNum); //这里使用desk里的函数
        hasScore += score;
        if (hasScore < 0) {
            hasScore = 0;
        }
        else if (hasScore > 1000000000) {
            hasScore = 1000000000;
        }
        return  writeScore(playerNum, hasScore)
    }

    fun readWin(playerNum: Long): Long {
        return GetPrivateProfileInt("win", playerNum.toString(), 0, CONFIG_PATH)
    }

    fun readLose(playerNum: Long): Long {
        return GetPrivateProfileInt("lose", playerNum.toString(), 0, CONFIG_PATH)
    }

    fun writeWin(playerNum: Long, win: Long): Boolean {
        return WritePrivateProfileString("win", playerNum.toString(), win.toString(), CONFIG_PATH)
    }

    fun writeLose(playerNum: Long, lose: Long): Boolean {
        return WritePrivateProfileString("lose", playerNum.toString(), lose.toString(), CONFIG_PATH)
    }

    fun addWin(playerNum: Long): Boolean {
        return writeWin(playerNum, readWin(playerNum) + 1)
    }

    fun addLose(playerNum: Long): Boolean {
        return writeLose(playerNum, readLose(playerNum) + 1)
    }

    fun readDataType(): String {
        return if(GetPrivateProfileInt("type", "isofficial", 0, CONFIG_PATH) != 0L) "正式数据" else "测试数据"
    }

    fun writeDataType(): Boolean {
        val value = 1 - GetPrivateProfileInt("type", "isofficial", 0, CONFIG_PATH)
        return WritePrivateProfileString("type", "isofficial", value.toString(), CONFIG_PATH)
    }

    fun readVersion(): Long {
        return GetPrivateProfileInt("version", "version", 0, CONFIG_PATH);
    }

    fun writeVersion(): Boolean {
        // TODO: 将时间作为版本写入
        /*
        wstringstream ss;
        ss.str(L"");


        time_t rawtime = time(0);
        char tmp[64] = "";
        struct tm now_time;
        localtime_s(&now_time, &rawtime);
        Util::strcat_tm(tmp, sizeof(tmp), now_time);

        ss << tmp;
        wstring value = ss.str();
        ss.str(L"");
         */
        return WritePrivateProfileString("version", "version", "Alpha", CONFIG_PATH);
    }

    fun IAmAdmin(playerNum: Long): Boolean {
        return readAdmin() == 0L && writeAdmin(playerNum)
    }

    fun resetGame(playNum: Long): Boolean {
        return false
        // TODO: allow reset the game
        // return playNum == readAdmin() && DeleteFile(CONFIG_PATH)
    }

    //私人查询信息
    fun getPlayerInfo(playNum: Long) {
        var msg = """[CQ:at,qq=${playNum }]：\n
                ${readWin(playNum)}胜
                ${readLose(playNum)}负，
            积分${(readScore(playNum) - 500000000L)}""".trimIndent()

        suspend { Util.sendPrivateMsg(playNum, msg) }
    }

    fun backupData(playNum: Long): Boolean {
        // TODO: 备份数据
        return false
        /*
        if (!isAdmin(playNum)) {
            return false;
        }
        assert(false)

        wstring msg;
        ifstream in;
        ofstream out;

        char *sourceFile = "./app/com.auntspecial.doudizhu/config.ini";
        char *targetFile_1 = "./app/com.auntspecial.doudizhu/config_";
        char *targetFile_2 = ".ini.bak";
        char targetFile[80] = {0};

        time_t rawtime = time(0);
        struct tm now_time;
        localtime_s(&now_time, &rawtime);

        strcpy_s(targetFile, sizeof(targetFile), targetFile_1);
        Util::strcat_tm(targetFile, sizeof(targetFile), now_time);
        strcat_s(targetFile, sizeof(targetFile), targetFile_2);

        in.open(sourceFile, ios::binary);//打开源文件
        if (in.fail())//打开源文件失败
        {
            //cout << "Error 1: Fail to open the source file." << endl;
            in.close();
            out.close();
            msg = L"源文件打开失败。";
            Util::sendPrivateMsg(playNum, Util::wstring2string(msg).data());

            return false;
        }
        out.open(targetFile, ios::binary);//创建目标文件
        if (out.fail())//创建文件失败
        {
            //cout << "Error 2: Fail to create the new file." << endl;
            out.close();
            in.close();
            msg = L"目标文件打开失败。\r\n" + Util::string2wstring(targetFile);
            Util::sendPrivateMsg(playNum, Util::wstring2string(msg).data());

            return false;
        }
        else//复制文件
        {
            out << in.rdbuf();
            out.close();
            in.close();

            msg = L"文件已备份至\r\n" + Util::string2wstring(targetFile);
            Util::sendPrivateMsg(playNum, Util::wstring2string(msg).data());

            return true;
        }
         */
    }
}
