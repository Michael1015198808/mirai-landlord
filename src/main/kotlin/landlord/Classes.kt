package michael.landlord.main

val DEBUG: Boolean = true
// Length: 54
val cardDest = arrayOf (
    "小王","大王",
    "2","3","4","5","6","7","8","9","10","J","Q","K","A",
    "2","3","4","5","6","7","8","9","10","J","Q","K","A",
    "2","3","4","5","6","7","8","9","10","J","Q","K","A",
    "2","3","4","5","6","7","8","9","10","J","Q","K","A"
)

// Length: 15
val flag = arrayOf ( "3","4","5","6","7","8","9","10","J","Q","K","A","2","小王","大王")

const val STATE_WAIT        = 0
const val STATE_START       = 1
const val STATE_BOSSING     = 2
const val STATE_MULTIPLING  = 3
const val STATE_READYTOGO   = 4
const val STATE_GAMING      = 5
const val STATE_OVER        = 6

const val CONFIG_PATH = "./app/com.auntspecial.doudizhu/config.ini"
const val CONFIG_DIR = "./app/com.auntspecial.doudizhu/"

const val CONFIG_INIT_SCORE         = 150L
const val CONFIG_BOTTOM_SCORE       = 3L
const val CONFIG_TOP_SCORE          = 1000L
const val CONFIG_PLAY_BONUS         = 0L
const val CONFIG_SURRENDER_PENALTY  = 150L
const val CONFIG_TIME_BOSS          = 30
const val CONFIG_TIME_MULTIPLE      = 30
const val CONFIG_TIME_GAME          = 60
const val CONFIG_TIME_WARNING       = 15
const val CONFIG_VERSION = "5.0.1 master 201802120029"

const val allotReg      = "设置积分(\\d+)=(\\d+)"
const val allotReg2     = "设置积分(\\d+)=-(\\d+)"
const val getInfoReg    = "查询积分(\\d+)"
const val numberReg     = "\\d+"
