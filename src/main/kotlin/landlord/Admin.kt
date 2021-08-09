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


    fun IAmAdmin(playerNum: Long): Boolean {
        return readAdmin() == 0L && writeAdmin(playerNum)
    }

    fun resetGame(playNum: Long): Boolean {
        return false
        // TODO: allow reset the game
        // return playNum == readAdmin() && DeleteFile(CONFIG_PATH)
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
